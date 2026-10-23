#!/usr/bin/env python3
"""Resumable, single-browser, wall-clock audit; downtime never counts as test time.

One live-provider scenario at a time. No crawling, tile prefetch or access bypass.
Snapshots the tested site. A partial route at the deadline is NOT a pass.
"""
import argparse
import fcntl
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
def now(): return datetime.now(timezone.utc).isoformat()
def save(path, value):
    tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(value,ensure_ascii=False,indent=2));tmp.replace(path)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--hours',type=float,default=5)
    parser.add_argument('--output',default='artifacts/world-soak-20261023')
    parser.add_argument('--port',type=int,default=4174)
    args=parser.parse_args()
    if not 0 < args.hours <= 24: parser.error('hours must be >0 and <=24')
    out=(ROOT/args.output).resolve();out.mkdir(parents=True,exist_ok=True)
    lock=(out/'runner.lock').open('a')
    try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError: raise SystemExit('This audit is already running; inspect status.json instead of starting a second writer.')
    state_path=out/'status.json'
    state=json.loads(state_path.read_text()) if state_path.exists() else {
        'startedAt':now(),'targetSeconds':args.hours*3600,'runningSeconds':0,
        'scope':'Paced synthetic GPS in the actual browser; live search, route and viewport tiles. Not physical driving or worldwide completeness.',
        'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'attempts':[],'interruptions':[]}
    if state.get('status')=='completed': print('Already completed:',state_path);return
    site=out/'site'
    if not site.exists(): shutil.copytree(ROOT/'build/site',site)
    manifest=ROOT/'scripts/web/world-soak-cases.cjs'
    cases=json.loads(subprocess.check_output(['node','-e',f'console.log(JSON.stringify(require({json.dumps(str(manifest))})))'],text=True))
    if (out/'cases.json').exists(): cases=json.loads((out/'cases.json').read_text())
    else: save(out/'cases.json',cases)
    for row in state['attempts']:
        if row.get('status')=='running':
            row['status']='interrupted';state['interruptions'].append({'at':now(),'case':row['id']})
    remaining=max(0,state['targetSeconds']-state['runningSeconds'])
    baseline=state['runningSeconds'];started=time.monotonic();deadline=time.time()+remaining
    state.update(status='running',resumedAt=now(),expectedFinishAt=datetime.fromtimestamp(deadline,timezone.utc).isoformat())
    save(state_path,state)
    server_log=(out/'server.log').open('a')
    server=subprocess.Popen(['python3','scripts/serve-web.py','--directory',str(site),'--port',str(args.port)],cwd=ROOT,stdout=server_log,stderr=subprocess.STDOUT)
    child=None;stop=False
    def interrupted(*_):
        nonlocal stop
        stop=True
    signal.signal(signal.SIGTERM,interrupted);signal.signal(signal.SIGINT,interrupted)
    try:
        time.sleep(1)
        if server.poll() is not None: raise RuntimeError('Audit preview server failed to start; see server.log')
        while time.time()<deadline and not stop:
            completed={a['id'] for a in state['attempts'] if a['status'] not in ('interrupted','running')}
            case=next((c for c in cases if c['id'] not in completed),None)
            if case is None:
                # Do not repeatedly query the same public search catalog.
                state['status']='catalog-exhausted';break
            attempt_dir=out/f"{len(state['attempts'])+1:03d}-{case['id']}";attempt_dir.mkdir()
            attempt={'id':case['id'],'country':case['country'],'status':'running','startedAt':now(),'directory':str(attempt_dir.relative_to(ROOT))}
            state['attempts'].append(attempt);save(state_path,state)
            # A JSON snapshot is also a valid CommonJS module: stable throughout this run.
            env={**os.environ,'CABIN_WORLD_MANIFEST':str(out/'cases.json'),'CABIN_WORLD_CASES':case['id'],
                 'CABIN_WORLD_OUT':str(attempt_dir),'CABIN_WORLD_REALTIME':'1','CABIN_WORLD_DEADLINE':str(int(deadline*1000)),
                 'CABIN_WORLD_SITE':str(site),'CABIN_DEMO_URL':f'http://127.0.0.1:{args.port}/'}
            print('START',case['id'],now(),flush=True)
            with (attempt_dir/'process.log').open('w') as log:
                child=subprocess.Popen(['node','scripts/web/verify-world-drive-live.cjs'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                while child.poll() is None and not stop:
                    time.sleep(5)
                    state.update(runningSeconds=baseline+time.monotonic()-started,updatedAt=now())
                    result_file=attempt_dir/'results.json'
                    if result_file.exists():
                        try:
                            row=json.loads(result_file.read_text())['scenarios'][-1]
                            attempt.update(gpsInputs=len(row['samples']),distanceMeters=row['samples'][-1]['atMeters'] if row['samples'] else 0,
                                           drivingWallSeconds=row.get('drivingWallSeconds',0),mapChecks=len(row['checkpoints']))
                        except (ValueError,KeyError,IndexError): pass
                    save(state_path,state)
                    if time.time()>deadline+60:
                        stop=True;attempt['status']='deadline-unresponsive';break
                if child.poll() is None:
                    os.killpg(child.pid,signal.SIGTERM)
                    try: child.wait(timeout=10)
                    except subprocess.TimeoutExpired: os.killpg(child.pid,signal.SIGKILL);child.wait()
                attempt.update(exitCode=child.returncode,finishedAt=now())
                result_file=attempt_dir/'results.json'
                if result_file.exists():
                    rows=json.loads(result_file.read_text())['scenarios']
                    attempt['status']=rows[-1]['result'] if rows else 'failed-startup'
                    if rows:
                        r=rows[-1];attempt.update(search=r['search'].get('result'),failure=r.get('failure'),
                            gpsInputs=len(r['samples']),drivingWallSeconds=r.get('drivingWallSeconds',0),
                            distanceMeters=r['samples'][-1]['atMeters'] if r['samples'] else 0,mapChecks=len(r['checkpoints']))
                else: attempt['status']='interrupted' if stop else 'failed-startup'
                print('FINISH',case['id'],attempt['status'],now(),flush=True);save(state_path,state)
                # Consecutive infrastructure failures: stop instead of hammering providers.
                if len(state['attempts'])>=3 and all(a['status']=='failed-startup' for a in state['attempts'][-3:]):
                    state['status']='blocked';break
            child=None
        state['runningSeconds']=baseline+time.monotonic()-started
        if state['status']=='running': state['status']='interrupted' if stop else 'completed'
    except Exception as error:
        state.update(status='blocked',failure=str(error))
        raise
    finally:
        if child is not None and child.poll() is None: os.killpg(child.pid,signal.SIGTERM)
        server.terminate()
        try: server.wait(timeout=10)
        except subprocess.TimeoutExpired: server.kill();server.wait()
        server_log.close()
        state['updatedAt']=now();save(state_path,state);lock.close()
    print(json.dumps({k:state[k] for k in ['status','runningSeconds','targetSeconds']},ensure_ascii=False),flush=True)

if __name__=='__main__': main()
