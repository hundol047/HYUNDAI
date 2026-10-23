// Deterministic regression replay of previously captured live routes, not new live-provider tests.
const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),rows=new Map();
const inputs=process.env.CABIN_WORLD_REPORT_LIST?JSON.parse(fs.readFileSync(path.resolve(root,process.env.CABIN_WORLD_REPORT_LIST))):process.env.CABIN_WORLD_BASELINE?[path.resolve(root,process.env.CABIN_WORLD_BASELINE)]:
 ['world-drive-live','world-drive-extended','world-drive-corrected'].map(name=>path.join(root,'artifacts',name,'results.json'));
for(const file of inputs){
 const dir=path.dirname(file),report=JSON.parse(fs.readFileSync(file));
 for(const row of report.scenarios)if(row.result==='passed'){
  const scenarioDir=fs.existsSync(path.join(dir,row.id,'route.json'))?path.join(dir,row.id):dir;
  const samples=row.samples||require('node:zlib').gunzipSync(fs.readFileSync(path.join(scenarioDir,row.samplesFile||'samples.jsonl.gz'))).toString().trim().split('\n').filter(Boolean).map(JSON.parse);
  rows.set(row.id,{...row,samples,scenarioDir});
 }
}
const rad=x=>x*Math.PI/180;
// Interpolate the short arc across ±180°, never through longitude zero.
const interpolateLongitude=(a,b,t)=>((a+((b-a+540)%360-180)*t+540)%360)-180;
function distance(a,b){return 6371000*2*Math.asin(Math.sqrt(Math.sin(rad(b[0]-a[0])/2)**2+Math.cos(rad(a[0]))*Math.cos(rad(b[0]))*Math.sin(rad(b[1]-a[1])/2)**2))}
function heading(a,b){return(Math.atan2(Math.sin(rad(b[1]-a[1]))*Math.cos(rad(b[0])),Math.cos(rad(a[0]))*Math.sin(rad(b[0]))-Math.sin(rad(a[0]))*Math.cos(rad(b[0]))*Math.cos(rad(b[1]-a[1])))*180/Math.PI+360)%360}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const report={type:'Offline shared production JS tracker replay of captured live-provider geometry and all original synthetic 1Hz positions. No new provider or map-rendering claim.',observedAt:new Date().toISOString(),expectedCases:rows.size,harnessSha256:crypto.createHash('sha256').update(fs.readFileSync(__filename)).digest('hex'),engineSha256:crypto.createHash('sha256').update(fs.readFileSync(path.resolve(root,process.env.CABIN_WORLD_SITE||'build/site','engine.js'))).digest('hex'),cases:[]};
 try{for(const row of rows.values()){
  const context=await browser.newContext(),page=await context.newPage();await page.route('https://**/*',r=>r.abort());
  await page.addInitScript(()=>{const now=Date.now.bind(Date);window.replayOffset=0;Date.now=()=>now()+replayOffset});
  const raw=fs.readFileSync(path.join(row.scenarioDir,'route.json'),'utf8'),r=JSON.parse(raw).routes[0],points=r.geometry.coordinates.map(p=>[p[1],p[0]]),cum=[0];for(let i=1;i<points.length;i++)cum.push(cum.at(-1)+distance(points[i-1],points[i]));
  let segment=0;const fixes=row.samples.map(sample=>{const at=sample.atMeters;while(segment<points.length-2&&cum[segment+1]<at)segment++;const length=cum[segment+1]-cum[segment],t=length?(at-cum[segment])/length:0;return{at,p:[points[segment][0]+(points[segment+1][0]-points[segment][0])*t,interpolateLongitude(points[segment][1],points[segment+1][1],t)],heading:heading(points[segment],points[segment+1]),previousRemaining:sample.remainingMeters,nearArrival:sample.arrived||sample.remainingMeters<50}});
  try{
   await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
   await page.evaluate(({raw,row,origin})=>{if(!CabinOS.navSetLanguage(row.language))throw Error('Language setup failed');CabinOS.navFix(origin[0],origin[1],5,Date.now(),0,-1);const error=CabinOS.navPreview(raw,row.label,row.end[0],row.end[1]);if(error)throw Error(error);if(!CabinOS.navStart(true))throw Error('Start failed')},{raw,row:{label:row.label,language:row.language,end:row.end},origin:points[0]});
   const samples=await page.evaluate(fixes=>fixes.map(f=>{replayOffset+=1000;CabinOS.navFix(f.p[0],f.p[1],5,Date.now(),5,f.heading);const n=JSON.parse(CabinOS.navState());return{at:f.at,nearArrival:f.nearArrival,tracking:n.tracking,offRoute:n.offRoute,active:n.active,arrived:n.arrived,symbol:n.turnSymbol,instruction:n.instruction,delta:Math.abs(n.remainingMeters-f.previousRemaining)}}),fixes);
   const end=await page.evaluate(()=>JSON.parse(CabinOS.navState())),finalLeg=samples.filter(s=>s.active&&s.symbol==='●');
   assert.equal(end.arrived,true);assert.ok(samples.every(s=>s.tracking&&!s.offRoute));assert.ok(samples.every(s=>s.delta<(s.nearArrival?50:.1)),'Progress must match except for the stricter uncertainty-aware arrival boundary');
   const completed=end.instruction.split(' · ')[0];if(row.id==='furka')assert.ok(finalLeg.length>0);assert.ok(finalLeg.every(s=>!s.instruction.includes(completed)),'Completed arrival must not be shown during the final leg');
   const result={id:row.id,result:'passed',samples:samples.length,finalLegSamples:finalLeg.length,maxProgressDeltaMeters:Math.max(...samples.map(s=>s.delta)),maxPreArrivalDeltaMeters:Math.max(...samples.filter(s=>!s.nearArrival).map(s=>s.delta)),upcomingInstructions:[...new Set(finalLeg.map(s=>s.instruction))],arrivalInstruction:end.instruction};report.cases.push(result);console.log('PASS REPLAY',row.id,samples.length);
  }finally{await context.close()}
 }}finally{await browser.close();const out=path.resolve(root,process.env.CABIN_REPLAY_OUT||'artifacts/world-route-replay.json');fs.mkdirSync(path.dirname(out),{recursive:true});report.complete=report.cases.length===report.expectedCases;fs.writeFileSync(out,JSON.stringify(report,null,2))}
})().catch(e=>{console.error(e);process.exitCode=1});
