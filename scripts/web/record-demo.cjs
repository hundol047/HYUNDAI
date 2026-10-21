const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..');
const draft=process.argv.includes('--draft');
let runningBrowser;
const out=path.join(root,draft?'artifacts/video-review/draft':'artifacts/video-review/final');
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],env:{...process.env,XDG_CACHE_HOME:process.env.XDG_CACHE_HOME||'/workspace/.cache'}});
 runningBrowser=browser;
 const page=await browser.newPage({viewport:{width:1600,height:1100},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');
 await page.waitForFunction(()=>window.cabinDemo?.state.parked,null,{timeout:20000});
 await page.evaluate(()=>{
  const c=document.createElement('div');c.id='record-caption';c.style.cssText='position:fixed;z-index:99;bottom:18px;left:50%;transform:translateX(-50%);padding:13px 25px;border:1px solid #62e2c355;border-radius:14px;background:#08131df2;color:#f0f6fa;text-align:center;max-width:90vw;font:500 17px sans-serif;pointer-events:none;box-shadow:0 8px 35px #0008';document.body.append(c);
 });
 const cdp=await page.context().newCDPSession(page),frames=[];
 cdp.on('Page.screencastFrame',async e=>{
  const file=path.join(out,`${String(frames.length).padStart(5,'0')}.jpg`);
  fs.writeFileSync(file,Buffer.from(e.data,'base64'));frames.push({file,at:Date.now()});
  await cdp.send('Page.screencastFrameAck',{sessionId:e.sessionId}).catch(()=>{});
 });
 await cdp.send('Page.startScreencast',{format:'jpeg',quality:92,maxWidth:1600,maxHeight:1100,everyNthFrame:1});
 const sleep=ms=>new Promise(r=>setTimeout(r,ms));
 async function caption(text,delay=2000){console.log(text);await page.evaluate(t=>document.querySelector('#record-caption').textContent=t,text);await sleep(delay)}
 async function vehicle(name){await page.locator(`#scenarios [data-vehicle="${name}"]`).click();await sleep(800)}
 await caption('CabinOS ONE · 퓨처랩스 — 브라우저 시뮬레이션과 Android 앱');
 await page.evaluate(()=>window.scrollTo(0,document.querySelector('.demo-toolbar').offsetTop-12));
 await vehicle('주행');await caption('01 주행 시작 — EchoRoad가 자동으로 활성화됩니다.',3000);
 await page.locator('.warnings [data-action="assistant"], .warnings [data-action="hazard-speak"]').click();
 await caption('주행 중에는 위험 안내와 간단한 음성 조작을 우선합니다.',2300);
 if(await page.locator('#assistant').evaluate(e=>e.open))await page.locator('[data-action="close-dialog"]').click();
 await vehicle('신호 대기');await caption('02 속도 0, D 기어 — 신호 대기에서는 정차 기능을 열지 않습니다.',3000);
 await vehicle('주차');await page.waitForFunction(()=>window.cabinDemo.state.parked);await caption('03 안전한 P 상태 확인 — StopSense와 CabinOS가 함께 작동합니다.',2300);
 await vehicle('충전');await caption('충전 대기 시간에서 출발 준비 여유를 제외해 활동 시간을 계산합니다.',2200);
 await page.locator('[data-start="FOCUS"]').click();await caption('04 25분 집중 세션 — StopSense와 동일한 출발 계획을 공유합니다.',2500);
 await page.locator('#task-form input').fill('공모전 시연 발표 준비');await page.locator('#task-form button').click();await sleep(900);await page.locator('[data-task="0"]').click();await caption('할 일을 추가하고 완료할 수 있습니다.',1800);
 await vehicle('충전 15분');await caption('05 충전 예상이 15분으로 변경 — 진행 중인 세션도 약 10분으로 조정됩니다.',3000);
 await page.locator('[data-action="pause"]').click();await caption('일시정지 후에도 출발 계획은 계속 유지됩니다.',1600);
 await page.locator('[data-action="pause"]').click();await sleep(700);await page.locator('[data-action="end"]').click();
 await page.locator('[data-start="RELAX"]').click();await caption('06 휴식 모드 — 호흡 안내와 공유 출발 타이머로 쉬어갑니다.',3000);
 await vehicle('출발 준비');await caption('07 출발 준비 시간 — 활동을 정리하고 출발을 안내합니다.',2400);
 await vehicle('주행');await caption('08 재출발 — 정차 조작을 제한하고 EchoRoad로 자동 복귀합니다.',2600);
 await page.locator('.fault-strip [data-vehicle="SDK 끊김"]').click();await caption('연결 손실 시에는 상태 미확인으로 전환하고 안전 제한을 적용합니다.',2200);
 await page.locator('[data-view="android"]').click();await caption('실제 Android 앱 — 동일한 Kotlin 엔진을 사용하는 Release APK를 제공합니다.',2800);
 await page.locator('[data-image="focus"]').click();await page.locator('#native-screen').scrollIntoViewIfNeeded();await caption('실제 Compose UI 렌더링 · 실차 캡처가 아닙니다. Pleos 실차 연동은 미완료입니다.',2500);
 await caption('퓨처랩스 · CabinOS ONE — 차량의 상황을 이해하고, 이동의 모든 순간을 연결하다.',2400);
 await cdp.send('Page.stopScreencast');
 const end=Date.now();
 fs.writeFileSync(path.join(out,'frames.txt'),frames.map((f,i)=>`file '${f.file}'\nduration ${Math.max(.04,((frames[i+1]?.at||end)-f.at)/1000).toFixed(3)}\n`).join('')+`file '${frames.at(-1).file}'\n`);
 fs.writeFileSync(path.join(out,'metadata.json'),JSON.stringify({type:'actual Chromium browser screencast; shared Kotlin simulation engine',frames:frames.length,seconds:(end-frames[0].at)/1000,errors},null,2));
 await browser.close();
 if(errors.length)throw new Error(errors.join('\n'));
 const output=path.join(root,draft?'artifacts/video-review/CabinOS_ONE_draft.mp4':'docs/video/CabinOS_ONE_demo.mp4');fs.mkdirSync(path.dirname(output),{recursive:true});
 const result=spawnSync('ffmpeg',['-y','-f','concat','-safe','0','-i',path.join(out,'frames.txt'),'-r','24','-c:v','libx264','-preset','medium','-crf','20','-g','48','-pix_fmt','yuv420p','-movflags','+faststart',output],{encoding:'utf8'});
 if(result.status!==0)throw new Error(result.stderr.slice(-3000));
 console.log('Recorded',output,fs.statSync(output).size,'bytes');
})().catch(async e=>{console.error(e);if(runningBrowser)await runningBrowser.close().catch(()=>{});process.exitCode=1});
