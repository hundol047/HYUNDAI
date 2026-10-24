// Recorded real route geometry, synthetic noise/loss; no new provider/map claim.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,process.env.CABIN_GPS_STRESS_OUT||'artifacts/world-gps-stress.json');
const baseline=path.join(root,'test-fixtures/validation/world-drive-v0103'),cases=JSON.parse(fs.readFileSync(path.join(baseline,'baseline.json'))).scenarios;
const phase=Number(process.env.CABIN_GPS_PHASE||0),headingJitter=Number(process.env.CABIN_GPS_HEADING_JITTER||0),noHeading=process.env.CABIN_GPS_NO_HEADING==='1',noSpeed=process.env.CABIN_GPS_NO_SPEED==='1',stationarySamples=Number(process.env.CABIN_GPS_STATIONARY_SAMPLES||0);
const rad=x=>x*Math.PI/180;
// Interpolate the short arc across ±180°, never through longitude zero.
const interpolateLongitude=(a,b,t)=>((a+((b-a+540)%360-180)*t+540)%360)-180;
function distance(a,b){return 6371000*2*Math.asin(Math.sqrt(Math.sin(rad(b[0]-a[0])/2)**2+Math.cos(rad(a[0]))*Math.cos(rad(b[0]))*Math.sin(rad(b[1]-a[1])/2)**2))}
function bearing(a,b){return(Math.atan2(Math.sin(rad(b[1]-a[1]))*Math.cos(rad(b[0])),Math.cos(rad(a[0]))*Math.sin(rad(b[0]))-Math.sin(rad(a[0]))*Math.cos(rad(b[0]))*Math.cos(rad(b[1]-a[1])))*180/Math.PI+360)%360}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const report={type:'Offline actual production JS tracker in Chromium; recorded real routes with deterministic bounded radial noise, a 12-second signal gap and precise final fixes. Ambiguous opposing-road fixes must hold directional guidance; their position error remains reported separately. No physical GPS or new live provider verification.',noisePhaseRadians:phase,headingJitterDegrees:headingJitter,noHeading,noSpeed,stationarySamples,observedAt:new Date().toISOString(),harnessSha256:crypto.createHash('sha256').update(fs.readFileSync(__filename)).digest('hex'),engineSha256:crypto.createHash('sha256').update(fs.readFileSync(path.resolve(root,process.env.CABIN_WORLD_SITE||'build/site','engine.js'))).digest('hex'),cases:[]};
 try{for(const row of cases.filter(s=>!process.env.CABIN_WORLD_CASES||process.env.CABIN_WORLD_CASES.split(',').includes(s.id))){
  const raw=fs.readFileSync(path.join(baseline,row.id,'route.json'),'utf8'),r=JSON.parse(raw).routes[0],points=r.geometry.coordinates.map(p=>[p[1],p[0]]),cum=[0];
  for(let i=1;i<points.length;i++)cum.push(cum.at(-1)+distance(points[i-1],points[i]));
  let segment=0;const fixes=[];
  for(let at=10;at<cum.at(-1)-100;at+=10){while(segment<points.length-2&&cum[segment+1]<at)segment++;const t=(at-cum[segment])/(cum[segment+1]-cum[segment]||1);fixes.push({at,p:[points[segment][0]+(points[segment+1][0]-points[segment][0])*t,interpolateLongitude(points[segment][1],points[segment+1][1],t)],bearing:bearing(points[segment],points[segment+1])})}
  const finalFixes=[];let finalSegment=0;
  for(let at=fixes.at(-1).at+5;;at=Math.min(cum.at(-1),at+5)){
   while(finalSegment<points.length-2&&cum[finalSegment+1]<at)finalSegment++;
   const t=(at-cum[finalSegment])/(cum[finalSegment+1]-cum[finalSegment]||1);
   finalFixes.push({p:[points[finalSegment][0]+(points[finalSegment+1][0]-points[finalSegment][0])*t,interpolateLongitude(points[finalSegment][1],points[finalSegment+1][1],t)],bearing:bearing(points[finalSegment],points[finalSegment+1])});
   if(at>=cum.at(-1))break;
  }
  for(const accuracy of (process.env.CABIN_GPS_ACCURACIES?process.env.CABIN_GPS_ACCURACIES.split(',').map(Number):[5,25,50])){
   const context=await browser.newContext(),page=await context.newPage();await page.route('https://**/*',r=>r.abort());
   await page.addInitScript(()=>{const now=Date.now.bind(Date);window.stressOffset=0;Date.now=()=>now()+stressOffset});
   try{
    await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
    const result=await page.evaluate(({raw,row,points,fixes,finalFixes,accuracy,total,routeDistance,phase,headingJitter,noHeading,noSpeed,stationarySamples})=>{
     CabinOS.navSetLanguage(row.language);CabinOS.navFix(...points[0],5,Date.now(),0,-1);
     const error=CabinOS.navPreview(raw,row.label,...row.end);if(error)throw Error(error);if(!CabinOS.navStart(true))throw Error('Start failed');
     const issues=[],positions=[];let staleVerified=false,recovered=false,maximumProgressErrorMeters=0,maximumConfidentProgressErrorMeters=0,uncertainInputs=0;
     for(let i=0;i<fixes.length;i++){
      const f=fixes[i];stressOffset+=1000;
      if(i===Math.floor(fixes.length/2)){
       // Repeated uncertain fixes while actually stationary, e.g. a traffic light.
       for(let hold=0;hold<stationarySamples;hold++){
        stressOffset+=1000;const angle=(i+hold)*2.399963229728653+phase,radius=accuracy-1;
        CabinOS.navFix(f.p[0]+Math.cos(angle)*radius/111195,f.p[1]+Math.sin(angle)*radius/(111195*Math.cos(f.p[0]*Math.PI/180)),accuracy,Date.now(),noSpeed?-1:0,-1);
        const paused=JSON.parse(CabinOS.navState());
        const error=Math.abs((1-paused.remainingMeters/routeDistance)*total-f.at);
        maximumProgressErrorMeters=Math.max(maximumProgressErrorMeters,error);
        if(paused.directionUncertain){uncertainInputs++;if(paused.instruction!=='GPS 위치·진행 방향 확인 중')issues.push({stage:'stationary',error:'Ambiguous GPS exposed a directional instruction'})}else maximumConfidentProgressErrorMeters=Math.max(maximumConfidentProgressErrorMeters,error);
        if(!paused.tracking||paused.offRoute||paused.arrived)issues.push({stage:'stationary',index:hold,atMeters:f.at,tracking:paused.tracking,offRoute:paused.offRoute,arrived:paused.arrived,status:paused.status});
       }
       stressOffset+=12000;CabinOS.tick();const n=JSON.parse(CabinOS.navState());staleVerified=!n.tracking&&!n.arrived;
      }
      // Accuracy describes a radius, not independent +/- errors on both axes.
      const angle=i*2.399963229728653+phase,radius=accuracy-1;
      const lat=f.p[0]+Math.cos(angle)*radius/111195,lon=f.p[1]+Math.sin(angle)*radius/(111195*Math.cos(f.p[0]*Math.PI/180));
      CabinOS.navFix(lat,lon,accuracy,Date.now(),noSpeed?-1:10,noHeading?-1:(f.bearing+Math.sin(i*1.7+phase)*headingJitter+360)%360);const n=JSON.parse(CabinOS.navState());
      if(i===Math.floor(fixes.length/2))recovered=n.tracking&&!n.offRoute;
      positions.push({at:f.at,lat,lon,bearing:f.bearing,remainingMeters:n.remainingMeters,offRoute:n.offRoute,tracking:n.tracking,directionUncertain:n.directionUncertain});
      const error=Math.abs((1-n.remainingMeters/routeDistance)*total-f.at);
      maximumProgressErrorMeters=Math.max(maximumProgressErrorMeters,error);
      if(n.directionUncertain){uncertainInputs++;if(n.instruction!=='GPS 위치·진행 방향 확인 중')issues.push({stage:'moving',error:'Ambiguous GPS exposed a directional instruction'})}else maximumConfidentProgressErrorMeters=Math.max(maximumConfidentProgressErrorMeters,error);
      if(!n.tracking||n.offRoute||n.arrived)issues.push({index:i,atMeters:f.at,latitude:lat,longitude:lon,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,status:n.status});
     }
     // A fresh, precise final approach; no arrival claim from noisy coordinates.
     for(const f of finalFixes){stressOffset+=1000;CabinOS.navFix(...f.p,5,Date.now(),5,f.bearing)}
     stressOffset+=1000;CabinOS.navFix(...points.at(-1),5,Date.now(),0,-1);
     return {inputs:fixes.length+finalFixes.length+1+stationarySamples,maximumProgressErrorMeters,maximumConfidentProgressErrorMeters,uncertainInputs,positions,issues:issues.slice(0,20),issueCount:issues.length,staleVerified,recovered,arrived:JSON.parse(CabinOS.navState()).arrived};
    },{raw,row:{language:row.language,label:row.label,end:row.end},points,fixes,finalFixes,accuracy,total:cum.at(-1),routeDistance:r.distance,phase,headingJitter,noHeading,noSpeed,stationarySamples});
    if(!process.env.CABIN_GPS_DEBUG)delete result.positions;result.id=row.id;result.accuracyMeters=accuracy;result.progressToleranceMeters=Math.max(75,accuracy*3);result.result=!result.issueCount&&result.staleVerified&&result.recovered&&result.arrived&&result.maximumConfidentProgressErrorMeters<=result.progressToleranceMeters?'passed':'failed';
    report.cases.push(result);console.log(result.result.toUpperCase(),row.id,accuracy,result.inputs,result.issueCount);
    if(result.result==='failed')process.exitCode=1;
   }finally{await context.close();fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2))}
  }
 }}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
