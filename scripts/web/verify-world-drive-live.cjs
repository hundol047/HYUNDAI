// Opt-in, sequential live-provider audit. Never a physical/worldwide completeness test.
// Public search/route/tile traffic is not intercepted. Only GPS and clock are synthetic.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.resolve(root,process.env.CABIN_WORLD_OUT||'artifacts/world-drive-live');
const scenarios=[
 ['busan','부산','KR','Asia','ko-KR',[35.1631,129.1637],[35.1557,129.1466],'스타벅스 해운대'],
 ['daegu','대구','KR','Asia','ko-KR',[35.8714,128.6014],[35.8586,128.5995],'스타벅스 대구 반월당'],
 ['daejeon','대전','KR','Asia','ko-KR',[36.3510,127.3850],[36.3273,127.4273],'성심당 대전'],
 ['jeju','제주','KR','Asia','ko-KR',[33.5060,126.4920],[33.5115,126.5270],'스타벅스 제주'],
 ['tokyo','도쿄','JP','Asia','ja-JP',[35.6690,139.7030],[35.6595,139.7005],'Starbucks Shibuya Tokyo'],
 ['beijing','베이징','CN','Asia','zh-CN',[39.9160,116.4100],[39.9300,116.4400],'Starbucks Beijing'],
 ['singapore','싱가포르','SG','Asia','en-SG',[1.3000,103.8390],[1.2860,103.8520],'Ya Kun Kaya Toast Singapore'],
 ['bangkok','방콕','TH','Asia','th-TH',[13.7560,100.5018],[13.7460,100.5340],'Starbucks Bangkok'],
 ['delhi','뉴델리','IN','Asia','hi-IN',[28.6330,77.2160],[28.6130,77.2300],'Saravana Bhavan New Delhi'],
 ['dubai','두바이','AE','Asia','ar-AE',[25.2170,55.2790],[25.1975,55.2795],'Starbucks Dubai Mall'],
 ['istanbul','이스탄불','TR','Europe/Asia','tr-TR',[41.0080,28.9780],[41.0365,28.9850],'Hafiz Mustafa Istanbul'],
 ['london','런던','GB','Europe','en-GB',[51.5075,-0.1280],[51.5140,-0.1490],'Fortnum Mason London'],
 ['berlin','베를린','DE','Europe','de-DE',[52.5163,13.3780],[52.5210,13.4090],'Dussmann Berlin'],
 ['nairobi','나이로비','KE','Africa','en-KE',[-1.2860,36.8170],[-1.2700,36.8070],'Java House Nairobi'],
 ['cape-town','케이프타운','ZA','Africa','en-ZA',[-33.9250,18.4240],[-33.9080,18.4190],'Truth Coffee Cape Town'],
 ['new-york','뉴욕','US','North America','en-US',[40.7580,-73.9855],[40.7350,-73.9900],'Katz Delicatessen New York'],
 ['toronto','토론토','CA','North America','en-CA',[43.6535,-79.3840],[43.6430,-79.3740],'Balzacs Toronto'],
 ['mexico-city','멕시코시티','MX','North America','es-MX',[19.4326,-99.1332],[19.4270,-99.1610],'Cafe de Tacuba Ciudad de Mexico'],
 ['sao-paulo','상파울루','BR','South America','pt-BR',[-23.5505,-46.6333],[-23.5614,-46.6559],'Padaria Bella Paulista Sao Paulo'],
 ['buenos-aires','부에노스아이레스','AR','South America','es-AR',[-34.6037,-58.3816],[-34.6150,-58.3730],'Cafe Tortoni Buenos Aires'],
 ['sydney','시드니','AU','Oceania','en-AU',[-33.8730,151.2060],[-33.857846,151.209777],'Queen Victoria Building Sydney'],
 ['auckland','오클랜드','NZ','Oceania','en-NZ',[-36.8580,174.7600],[-36.8440,174.7700],'SkyCity Auckland'],
 ['furka','스위스 푸르카 산악도로','CH','Europe','de-CH',[46.586213,8.430412],[46.565596,8.38096],'Hotel Belvedere Furka'],
 ['tromso','노르웨이 트롬쇠','NO','Europe','nb-NO',[69.6510,18.9550],[69.6820,18.9200],'Fiskekompaniet Tromso'],
 ['queenstown','퀸스타운→애로타운','NZ','Oceania','en-NZ',[-45.0310,168.6620],[-44.9390,168.8300],'The Remarkables Sweet Shop Arrowtown'],
 ['gangwon','강원 대관령 주변','KR','Asia','ko-KR',[37.6868,128.7550],[37.7210,128.8260],'대관령 옛길']
].map(([id,label,country,continent,language,start,end,query])=>({id,label,country,continent,language,start,end,query}));
const rad=x=>x*Math.PI/180;
function distance(a,b){return 6371000*2*Math.asin(Math.sqrt(Math.sin(rad(b[0]-a[0])/2)**2+Math.cos(rad(a[0]))*Math.cos(rad(b[0]))*Math.sin(rad(b[1]-a[1])/2)**2))}
function heading(a,b){return(Math.atan2(Math.sin(rad(b[1]-a[1]))*Math.cos(rad(b[0])),Math.cos(rad(a[0]))*Math.sin(rad(b[0]))-Math.sin(rad(a[0]))*Math.cos(rad(b[0]))*Math.cos(rad(b[1]-a[1])))*180/Math.PI+360)%360}
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const proxy=process.env.HTTPS_PROXY||process.env.HTTP_PROXY;
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],...(proxy?{proxy:{server:proxy,bypass:'localhost,127.0.0.1'}}:{})});
 const report={observedAt:new Date().toISOString(),sourceCommit:require('node:child_process').execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),navigationSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'build/site/navigation.js'))).digest('hex'),engineSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'build/site/engine.js'))).digest('hex'),scope:'Selected urban, mountain and high-latitude routes, six inhabited continents; live providers, UI coordinate route selection, synthetic 1Hz 5m/s GPS. Shared tracker processes every position; map renders only at bounded checkpoints. Business searches are independent of coordinate destinations. No exhaustive business catalog, real GPS, traffic or real driving verification.',scenarios:[]};
 const save=()=>fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));
 try{for(const s of scenarios.filter(s=>!process.env.CABIN_WORLD_CASES||process.env.CABIN_WORLD_CASES.split(',').includes(s.id))){
  console.log('START',s.id);const dir=path.join(out,s.id);fs.mkdirSync(dir,{recursive:true});
  const context=await browser.newContext({viewport:{width:1440,height:1050},...(process.env.CABIN_WORLD_RECORD?{recordVideo:{dir,size:{width:1440,height:1050}}}:{})}),page=await context.newPage();
  const row={...s,result:'failed',search:{},responses:[],requestFailures:[],pageErrors:[],checkpoints:[],samples:[]};report.scenarios.push(row);const pending=[];let route;
  page.on('pageerror',e=>row.pageErrors.push(e.message));
  page.on('requestfailed',r=>{if(/^https:/.test(r.url()))row.requestFailures.push({url:r.url(),error:r.failure()?.errorText})});
  page.on('response',r=>{if(/^https:\/\/(tile\.openstreetmap\.org|nominatim\.openstreetmap\.org|router\.project-osrm\.org)\//.test(r.url())){row.responses.push({url:r.url(),status:r.status()});if(r.url().includes('/route/v1/'))pending.push(r.json().then(j=>{route=j;fs.writeFileSync(path.join(dir,'route.json'),JSON.stringify(j))}).catch(()=>{}));if(r.url().includes('/search?'))pending.push(r.json().then(j=>{row.search.providerResults=j;fs.writeFileSync(path.join(dir,'search.json'),JSON.stringify(j))}).catch(()=>{}));}});
  await page.addInitScript(start=>{const now=Date.now.bind(Date);window.worldOffset=0;Date.now=()=>now()+worldOffset;window.worldCoords={latitude:start[0],longitude:start[1],speed:0,accuracy:5,heading:null};Object.defineProperty(navigator,'geolocation',{configurable:true,value:{watchPosition(ok){window.worldGPS=()=>ok({coords:{...worldCoords},timestamp:Date.now()});worldGPS();window.worldTimer=setInterval(worldGPS,1000);return 1},clearWatch(){clearInterval(worldTimer)}}})},s.start);
  async function search(query){await page.locator('#nav-query').fill(query);await page.locator('#nav-search').click();await page.waitForFunction(()=>!document.querySelector('#nav-search').disabled,null,{timeout:55000});await Promise.all(pending)}
  async function checkpoint(at,label){
   await page.evaluate(label=>{document.querySelector('#world-proof').textContent=label+' · 실제 서버 / 합성 GPS / 구간별 지도 점검';worldGPS();cabinDemo.render()},s.label+' '+label);
   const before=Date.now();let timeout=false;
   try{await page.waitForFunction(()=>{const box=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const r=i.getBoundingClientRect();return r.right>box.left&&r.left<box.right&&r.bottom>box.top&&r.top<box.bottom});return tiles.length>=4&&tiles.every(i=>i.complete&&i.naturalWidth>0&&+getComputedStyle(i).opacity>.95)},null,{timeout:20000})}catch{timeout=true}
   const state=await page.evaluate(()=>{const box=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const r=i.getBoundingClientRect();return r.right>box.left&&r.left<box.right&&r.bottom>box.top&&r.top<box.bottom}),n=JSON.parse(CabinOS.navState());return{visible:tiles.length,decoded:tiles.filter(i=>i.complete&&i.naturalWidth>0).length,instruction:n.instruction,instructionLanguage:n.instructionLanguage,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,planningHidden:document.querySelector('#nav-planning').hidden,dragEnabled:worldMap.dragging.enabled(),zoom:worldMap.getZoom(),centerGap:n.fix?worldMap.getCenter().distanceTo([n.fix.latitude,n.fix.longitude]):null}});
   const capture=`checkpoint-${String(row.checkpoints.length).padStart(2,'0')}.png`;await page.screenshot({path:path.join(dir,capture),fullPage:true});row.checkpoints.push({atMeters:at,label,timeout,waitMs:Date.now()-before,...state,capture});
   console.log('ROAD',s.id,Math.round(at),`${state.decoded}/${state.visible}`,timeout?'TIMEOUT':'READY');
  }
  try{
   await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
   await page.evaluate(()=>{const f=L.map;L.map=function(...a){return window.worldMap=f.apply(this,a)}});
   await page.locator('aside [data-page="settings"]').click();await page.locator('[data-palette="sand"]').click();await page.locator('aside [data-page="navigation"]').click();
   await page.evaluate(()=>{const p=document.createElement('p');p.id='world-proof';p.style.cssText='color:#ffcb7a;font-size:14px';p.textContent='전세계 표본 경로 검사 · 실제 지도 / 합성 GPS';document.querySelector('#navigation-panel h2').after(p);window.scrollTo(0,document.querySelector('.device').offsetTop-12)});
   await page.locator('[data-nav="locate"]').click();await page.evaluate(language=>{EchoNavigation.setLanguage(language);cabinDemo.render()},s.language);
   await search(s.query);row.search.uiCount=await page.locator('[data-nav-place]').count();row.search.notice=await page.locator('#nav-notice').textContent();row.search.results=await page.locator('[data-nav-place]').allTextContents();
   row.search.result=row.search.uiCount?'returned-results':Array.isArray(row.search.providerResults)?'empty':'provider-error';
   row.search.nearestResultMeters=Array.isArray(row.search.providerResults)&&row.search.providerResults.length?Math.min(...row.search.providerResults.map(p=>distance(s.start,[+p.lat,+p.lon]))):null;
   await page.screenshot({path:path.join(dir,'search.png'),fullPage:true});
   await search(s.end.join(','));await page.locator('[data-nav-place="0"]').click();await page.waitForSelector('[data-nav="start"]',{timeout:40000});await Promise.all(pending);assert.equal(route?.code,'Ok');
   const r=route.routes[0],points=r.geometry.coordinates.map(p=>[p[1],p[0]]),cum=[0];for(let i=1;i<points.length;i++)cum.push(cum.at(-1)+distance(points[i-1],points[i]));const total=cum.at(-1);
   const steps=r.legs.flatMap(l=>l.steps).map(step=>{let closest=0,gap=Infinity;for(let i=0;i<points.length;i++){const d=distance(points[i],[step.maneuver.location[1],step.maneuver.location[0]]);if(d<gap){closest=i;gap=d}}return{atMeters:cum[closest],type:step.maneuver.type,modifier:step.maneuver.modifier,road:step.name,destinations:step.destinations,drivingSide:step.driving_side,exit:step.maneuver.exit}});
   row.route={distanceMeters:r.distance,geometryMeters:total,durationSeconds:r.duration,points:points.length,steps,originSnapMeters:distance(s.start,points[0]),destinationSnapMeters:distance(s.end,points.at(-1))};
   assert.ok(total>100&&total<100000,'Selected test route must be 0.1–100km');
   const stops=[0,total*.25,total*.5,total*.75];for(const step of steps.filter(x=>['roundabout','rotary','fork','off ramp','on ramp'].includes(x.type)).slice(0,3))stops.push(step.atMeters);
   stops.sort((a,b)=>a-b);const checks=stops.filter((v,i)=>!i||v-stops[i-1]>100);if(total-checks.at(-1)>80)checks.push(total-50);checks.push(total);
   await page.evaluate(p=>{Object.assign(worldCoords,{latitude:p[0],longitude:p[1]});worldGPS();worldMap.stop();worldMap.setZoom(16,{animate:false})},points[0]);
   await page.waitForTimeout(400);
   if(await page.locator('#nav-access-confirm').count())await page.locator('#nav-access-confirm').check();await page.locator('[data-nav="start"]').click();await page.waitForFunction(()=>JSON.parse(CabinOS.navState()).active);await checkpoint(0,'출발');
   let at=0,segment=0;
   for(const target of checks.slice(1)){
    const batch=[];while(at<target){at=Math.min(target,at+5);while(segment<points.length-2&&cum[segment+1]<at)segment++;const length=cum[segment+1]-cum[segment],t=length?(at-cum[segment])/length:0;batch.push({at,p:[points[segment][0]+(points[segment+1][0]-points[segment][0])*t,points[segment][1]+(points[segment+1][1]-points[segment][1])*t],heading:heading(points[segment],points[segment+1])})}
    const samples=await page.evaluate(batch=>batch.map(s=>{worldOffset+=1000;Object.assign(worldCoords,{latitude:s.p[0],longitude:s.p[1],speed:5,heading:s.heading});const before=performance.now();CabinOS.navFix(s.p[0],s.p[1],5,Date.now(),5,s.heading);const n=JSON.parse(CabinOS.navState());return{atMeters:s.at,remainingMeters:n.remainingMeters,instruction:n.instruction,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,updateMs:performance.now()-before}}),batch);row.samples.push(...samples);
    for(const sample of samples){assert.equal(sample.tracking,true,`Lost tracking at ${sample.atMeters}`);assert.equal(sample.offRoute,false,`False off-route at ${sample.atMeters}`);assert.ok(Math.abs(sample.remainingMeters-r.distance*(1-sample.atMeters/total))<80,`Wrong progress at ${sample.atMeters}`)}
    await checkpoint(target,target===total?'도착':`${Math.round(target)}m`);const c=row.checkpoints.at(-1);assert.equal(c.planningHidden,true);assert.equal(c.dragEnabled,false);if(!c.arrived)assert.ok(c.centerGap<10,'Active map must follow GPS');else assert.ok(c.centerGap<80,'Arrival must remain near the visible vehicle position');assert.ok(await page.locator('#nav-symbol svg path,#nav-symbol svg circle').count(),'Maneuver icon must render without font dependency');
   }
   await page.evaluate(()=>{worldOffset+=1000;worldCoords.speed=0;worldGPS();cabinDemo.render()});row.finalState=await page.evaluate(()=>JSON.parse(CabinOS.navState()));assert.equal(row.finalState.arrived,true);assert.equal(await page.locator('#nav-remaining').evaluate(e=>e.closest('p').hidden),true);
   // Genuine parked-state debounce before enabling zoom; never bypass driving controls.
   for(let i=0;i<6;i++){await page.waitForTimeout(1000);await page.evaluate(()=>worldGPS())}await page.evaluate(()=>{CabinOS.vehicle('주차');cabinDemo.render()});await page.waitForFunction(()=>cabinDemo.state.parked);
   const parkedZoom=await page.evaluate(()=>worldMap.getZoom());row.parkedZoom=parkedZoom;
   await page.locator('#navigation-map .leaflet-control-zoom-in').click();await page.waitForFunction(z=>worldMap.getZoom()===z+1,parkedZoom);await checkpoint(total,'주차 후 확대');
   await page.locator('#navigation-map').screenshot({path:path.join(dir,'map-detail.png')});
   await page.locator('#navigation-map .leaflet-control-zoom-out').click();await page.waitForFunction(z=>worldMap.getZoom()===z,parkedZoom);row.zoomInOutVerified=true;
   assert.ok(row.checkpoints.every(c=>!c.timeout&&c.visible===c.decoded),'One or more viewport tile checks failed');assert.deepEqual(row.pageErrors,[]);row.result='passed';console.log('PASS',s.id,Math.round(r.distance),'meters',row.samples.length,'fixes',row.search.result);
  }catch(e){row.failure=e.message;row.failureStack=e.stack;row.notice=await page.locator('#nav-notice').textContent().catch(()=>'');await page.screenshot({path:path.join(dir,'failure.png'),fullPage:true}).catch(()=>{});console.error('FAIL',s.id,e.message);process.exitCode=1}
  finally{await Promise.all(pending);const video=page.video();await context.close();if(video)row.video=path.basename(await video.path());save()}
 }}finally{await browser.close();save()}
})().catch(e=>{console.error(e);process.exitCode=1});
