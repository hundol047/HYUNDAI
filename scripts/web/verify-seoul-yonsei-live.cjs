// Opt-in: actual public providers, explicitly accelerated synthetic GPS.
// All intermediate 1 Hz positions run through the shared tracker. The UI/map
// renders at bounded road checkpoints; this is not a continuous real-time drive.
const {chromium}=require('playwright'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'artifacts/seoul-yonsei-live');fs.mkdirSync(out,{recursive:true});
const rad=d=>d*Math.PI/180;
function distance(a,b){return 6371000*2*Math.asin(Math.sqrt(Math.sin(rad(b[0]-a[0])/2)**2+Math.cos(rad(a[0]))*Math.cos(rad(b[0]))*Math.sin(rad(b[1]-a[1])/2)**2))}
function heading(a,b){return(Math.atan2(Math.sin(rad(b[1]-a[1]))*Math.cos(rad(b[0])),Math.cos(rad(a[0]))*Math.sin(rad(b[0]))-Math.sin(rad(a[0]))*Math.cos(rad(b[0]))*Math.cos(rad(b[1]-a[1])))*180/Math.PI+360)%360}
(async()=>{
 const proxy=process.env.HTTPS_PROXY||process.env.HTTP_PROXY;
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],...(proxy?{proxy:{server:proxy,bypass:'127.0.0.1,localhost'}}:{})});
 const context=await browser.newContext({viewport:{width:1440,height:1050},recordVideo:{dir:out,size:{width:1440,height:1050}}}),page=await context.newPage();
 const report={observedAt:new Date().toISOString(),result:'failed',type:'Actual Chromium and live Nominatim/OSRM/OSM; full route tracker receives synthetic 1 Hz GPS. Map is inspected at bounded checkpoints, not every intervening frame. No provider interception or fabricated roads.',physicalGps:false,physicalDriving:false,allBusinessesGuaranteed:false,sourceSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'build/site/navigation.js'))).digest('hex'),responses:[],requestFailures:[],pageErrors:[],searches:[],samples:[],checkpoints:[]};
 let route;const pending=[];
 page.on('pageerror',e=>report.pageErrors.push(e.message));page.on('requestfailed',r=>{if(/^https:/.test(r.url()))report.requestFailures.push({host:new URL(r.url()).host,error:r.failure()?.errorText})});
 page.on('response',r=>{if(/^https:\/\/(tile\.openstreetmap\.org|nominatim\.openstreetmap\.org|router\.project-osrm\.org)\//.test(r.url())){
  report.responses.push({url:r.url(),status:r.status()});
  if(r.url().includes('/route/v1/'))pending.push(r.json().then(j=>{route=j;fs.writeFileSync(path.join(out,'route.json'),JSON.stringify(j))}).catch(()=>{}));
  if(r.url().includes('/search?'))pending.push(r.json().then(j=>{const q=new URL(r.url()).searchParams.get('q');report.searches.push({query:q,results:j});}).catch(()=>{}));
 }});
 await page.addInitScript(()=>{const now=Date.now.bind(Date);window.yonseiOffset=0;Date.now=()=>now()+yonseiOffset;window.yonseiCoords={latitude:37.5667893,longitude:126.9784204,accuracy:5,speed:0,heading:null};Object.defineProperty(navigator,'geolocation',{configurable:true,value:{watchPosition(success){window.yonseiGPS=()=>success({coords:{...yonseiCoords},timestamp:Date.now()});yonseiGPS();window.yonseiTimer=setInterval(yonseiGPS,1000);return 1},clearWatch(){clearInterval(yonseiTimer)}}})});
 async function search(q){await page.locator('#nav-query').fill(q);await page.locator('#nav-search').click();await page.waitForFunction(()=>!document.querySelector('#nav-search').disabled,null,{timeout:55000});await Promise.all(pending)}
 async function checkpoint(label,at){
  await page.evaluate(label=>{document.querySelector('#yonsei-proof').textContent='서울시청 → 연세대학교 미래캠퍼스 · '+label+' · 실제 서버 / 합성 GPS·가속 지도 점검 · 실차 아님';yonseiGPS();cabinDemo.render()},label);
  const started=Date.now();let timeout=false;
  try{await page.waitForFunction(()=>{const r=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const b=i.getBoundingClientRect();return b.right>r.left&&b.left<r.right&&b.bottom>r.top&&b.top<r.bottom});return tiles.length>=4&&tiles.every(i=>i.complete&&i.naturalWidth>0&&+getComputedStyle(i).opacity>.95)},null,{timeout:20000})}catch{timeout=true}
  const snapshot=await page.evaluate(()=>{const r=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const b=i.getBoundingClientRect();return b.right>r.left&&b.left<r.right&&b.bottom>r.top&&b.top<r.bottom});const n=JSON.parse(CabinOS.navState());return{visible:tiles.length,decoded:tiles.filter(i=>i.complete&&i.naturalWidth>0).length,zoom:yonseiMap.getZoom(),instruction:n.instruction,remainingMeters:n.remainingMeters,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,planningHidden:document.querySelector('#nav-planning').hidden,dragEnabled:yonseiMap.dragging.enabled(),notice:document.querySelector('#nav-map-status').textContent,centerDistance:n.fix?yonseiMap.getCenter().distanceTo([n.fix.latitude,n.fix.longitude]):null}});
  const id=String(report.checkpoints.length).padStart(2,'0'),file=`checkpoint-${id}.png`;
  await page.screenshot({path:path.join(out,file),fullPage:true});report.checkpoints.push({label,atMeters:at,waitMs:Date.now()-started,timeout,...snapshot,capture:file});
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));console.log('ROAD',id,Math.round(at),label,`${snapshot.decoded}/${snapshot.visible}`,timeout?'TIMEOUT':'READY');
 }
 try{
  await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
  await page.evaluate(()=>{const f=L.map;L.map=function(...a){return window.yonseiMap=f.apply(this,a)}});
  await page.locator('aside [data-page="settings"]').click();await page.locator('[data-palette="sand"]').click();await page.locator('aside [data-page="navigation"]').click();
  await page.evaluate(()=>{const p=document.createElement('p');p.id='yonsei-proof';p.style.cssText='color:#ffcb7a;font-size:14px';p.textContent='실제 가게·도로 검색 / GPS는 검사용 합성 좌표';document.querySelector('#navigation-panel h2').after(p);window.scrollTo(0,document.querySelector('.device').offsetTop-12)});
  await page.locator('[data-nav="locate"]').click();
  await search('스타벅스 원주무실');report.spacingSearch={count:await page.locator('[data-nav-place]').count(),notice:await page.locator('#nav-notice').textContent()};assert.ok(report.spacingSearch.count>0);assert.match(report.spacingSearch.notice,/띄어쓰기/);await page.screenshot({path:path.join(out,'spacing-search.png'),fullPage:true});
  await search('이디야 매지리');report.missingSearch={count:await page.locator('[data-nav-place]').count(),notice:await page.locator('#nav-notice').textContent()};await page.screenshot({path:path.join(out,'missing-search.png'),fullPage:true});
  await search('연세대학교 미래캠퍼스');assert.ok(await page.locator('[data-nav-place="0"]').count());await page.locator('[data-nav-place="0"]').click();await page.waitForSelector('[data-nav="start"]',{timeout:40000});await Promise.all(pending);assert.equal(route?.code,'Ok');
  const r=route.routes[0],points=r.geometry.coordinates.map(p=>[p[1],p[0]]),cum=[0];for(let i=1;i<points.length;i++)cum.push(cum.at(-1)+distance(points[i-1],points[i]));const total=cum.at(-1);
  const steps=r.legs.flatMap(l=>l.steps).map(s=>{let closest=0,gap=Infinity;for(let i=0;i<points.length;i++){const d=distance(points[i],[s.maneuver.location[1],s.maneuver.location[0]]);if(d<gap){gap=d;closest=i}}return{atMeters:cum[closest],type:s.maneuver.type,modifier:s.maneuver.modifier,road:s.name,ref:s.ref,destinations:s.destinations,location:s.maneuver.location}});
  report.route={distanceMeters:r.distance,durationSeconds:r.duration,geometryMeters:total,points:points.length,steps,destinationAccessMeters:JSON.parse(await page.evaluate(()=>CabinOS.navState())).destinationAccessMeters};assert.ok(r.distance>100000&&r.distance<160000);assert.ok(steps.some(s=>s.type==='off ramp'));
  const candidate=[{at:0,label:'서울시청 출발'}];for(let at=5000;at<total;at+=5000)candidate.push({at,label:`${Math.round(at/1000)}km 도로`});
  for(const s of steps){candidate.push({at:s.atMeters,label:[s.type,s.road,s.destinations].filter(Boolean).join(' · ')});if(['merge','fork','off ramp','on ramp'].includes(s.type)){candidate.push({at:Math.max(0,s.atMeters-120),label:'진출입·분기 접근'});candidate.push({at:Math.min(total,s.atMeters+120),label:'진출입·분기 통과'})}}
  candidate.sort((a,b)=>a.at-b.at);const checkpoints=[];for(const c of candidate){if(!checkpoints.length||c.at-checkpoints.at(-1).at>=220)checkpoints.push(c);else checkpoints.at(-1).label+=' / '+c.label}checkpoints.push({at:total,label:'미래캠퍼스 차량 접근 지점 도착'});
  report.checkpointPolicy='Every ~5km plus every maneuver location, merging neighboring checks within 220m. Extra ramp/fork approach checks. Only visible viewport tiles are requested by Leaflet; no prefetch or offline download.';
  await page.evaluate(p=>{Object.assign(yonseiCoords,{latitude:p[0],longitude:p[1]});yonseiGPS();yonseiMap.stop();yonseiMap.setZoom(15,{animate:false})},points[0]);
  if(await page.locator('#nav-access-confirm').count())await page.locator('#nav-access-confirm').check();await page.locator('[data-nav="start"]').click();await page.waitForFunction(()=>JSON.parse(CabinOS.navState()).active);await checkpoint(checkpoints[0].label,0);
  // Intermediate fixes exercise the production tracker without issuing thousands
  // of transient viewport requests. Checkpoint fixes use the normal GPS callback.
  let at=0,segment=0,stage=1,stepIndex=0;
  while(at<total){
   const target=checkpoints[stage].at,batch=[];
   while(at<target){while(stepIndex<steps.length-1&&steps[stepIndex+1].atMeters<=at)stepIndex++;const near=steps.some(s=>Math.abs(s.atMeters-at)<180),speed=near||total-at<100?5:(steps[stepIndex].road||'').includes('고속도로')?25:12;at=Math.min(target,at+speed);while(segment<points.length-2&&cum[segment+1]<at)segment++;const len=cum[segment+1]-cum[segment],t=len?(at-cum[segment])/len:0,p=[points[segment][0]+(points[segment+1][0]-points[segment][0])*t,points[segment][1]+(points[segment+1][1]-points[segment][1])*t];batch.push({at,p,speed,heading:heading(points[segment],points[segment+1])})}
   const results=await page.evaluate(batch=>batch.map(s=>{yonseiOffset+=1000;Object.assign(yonseiCoords,{latitude:s.p[0],longitude:s.p[1],speed:s.speed,heading:s.heading});const start=performance.now();CabinOS.navFix(s.p[0],s.p[1],5,Date.now(),s.speed,s.heading);const n=JSON.parse(CabinOS.navState());return{atMeters:s.at,remainingMeters:n.remainingMeters,instruction:n.instruction,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,updateMs:performance.now()-start}}),batch);
   report.samples.push(...results);for(const s of results){assert.equal(s.tracking,true,`Tracking lost at ${s.atMeters}`);assert.equal(s.offRoute,false,`False off-route at ${s.atMeters}`);assert.ok(Math.abs(s.remainingMeters-r.distance*(1-s.atMeters/total))<80,`Wrong progress at ${s.atMeters}`)}
   await checkpoint(checkpoints[stage].label,at);assert.equal(report.checkpoints.at(-1).planningHidden,true);assert.equal(report.checkpoints.at(-1).dragEnabled,false);stage++;
   if(at<total)await page.waitForTimeout(300);
  }
  await page.evaluate(()=>{yonseiOffset+=1000;Object.assign(yonseiCoords,{speed:0});yonseiGPS();cabinDemo.render()});report.finalState=await page.evaluate(()=>JSON.parse(CabinOS.navState()));assert.equal(report.finalState.arrived,true);await checkpoint('연세대학교 미래캠퍼스 도착 확인',total);
  assert.ok(report.samples.some(s=>s.instruction.includes('남원주')&&s.instruction.includes('충주')),'Namwonju destination sign must survive the provider parser');
  assert.equal(await page.locator('#nav-remaining').evaluate(e=>e.closest('p').hidden),true,'Do not display stale driving distance after arrival');
  // Reconfirm parking with continuous fresh stationary evidence before using
  // normal map zoom controls; do not bypass the product's driving lock.
  for(let i=0;i<6;i++){await page.waitForTimeout(1000);await page.evaluate(()=>yonseiGPS())}
  await page.evaluate(()=>{CabinOS.vehicle('주차');cabinDemo.render()});await page.waitForFunction(()=>cabinDemo.state.parked);
  for(const zoom of [16,17]){await page.locator('#navigation-map .leaflet-control-zoom-in').click();await page.waitForFunction(z=>yonseiMap.getZoom()===z,zoom);await page.waitForTimeout(350)}
  await checkpoint('주차 확인 후 캠퍼스 확대·상호 표시 점검',total);
  report.campusDetail=report.checkpoints.pop();fs.copyFileSync(path.join(out,report.campusDetail.capture),path.join(out,'campus-detail.png'));report.campusDetail.capture='campus-detail.png';
  await search('우리은행 연세대미래캠퍼스');report.campusBusinessSearch={count:await page.locator('[data-nav-place]').count(),notice:await page.locator('#nav-notice').textContent()};await page.screenshot({path:path.join(out,'campus-business-search.png'),fullPage:true});
  assert.ok(report.checkpoints.every(c=>!c.timeout&&c.decoded===c.visible));assert.deepEqual(report.pageErrors,[]);report.result='passed';console.log('PASS SEOUL TO YONSEI',r.distance,'meters',report.samples.length,'GPS inputs',report.checkpoints.length,'road checks');
 }catch(e){report.failure=e.message;report.notice=await page.locator('#nav-notice').textContent().catch(()=>'');await page.screenshot({path:path.join(out,'failure.png'),fullPage:true}).catch(()=>{});console.error(e);process.exitCode=1}
 finally{await Promise.all(pending);const video=page.video();await context.close();report.video=path.basename(await video.path());fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
