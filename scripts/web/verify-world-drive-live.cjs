// Opt-in, sequential live-provider audit. Never a physical/worldwide completeness test.
// Public search/route/tile traffic is not intercepted. Only GPS and clock are synthetic.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.resolve(root,process.env.CABIN_WORLD_OUT||'artifacts/world-drive-live');
const scenarios=require(process.env.CABIN_WORLD_MANIFEST?path.resolve(process.env.CABIN_WORLD_MANIFEST):'./world-drive-cases.cjs');
const realtime=process.env.CABIN_WORLD_REALTIME==='1',deadline=Number(process.env.CABIN_WORLD_DEADLINE)||Infinity;
const pace=Number(process.env.CABIN_WORLD_SPEED_MPS)||10;assert.ok(pace>=5&&pace<=25);
const site=path.resolve(process.env.CABIN_WORLD_SITE||path.join(root,'build/site'));
const rad=x=>x*Math.PI/180;
// Interpolate the short arc across ±180°, never through longitude zero.
const interpolateLongitude=(a,b,t)=>((a+((b-a+540)%360-180)*t+540)%360)-180;
function distance(a,b){return 6371000*2*Math.asin(Math.sqrt(Math.sin(rad(b[0]-a[0])/2)**2+Math.cos(rad(a[0]))*Math.cos(rad(b[0]))*Math.sin(rad(b[1]-a[1])/2)**2))}
function heading(a,b){return(Math.atan2(Math.sin(rad(b[1]-a[1]))*Math.cos(rad(b[0])),Math.cos(rad(a[0]))*Math.sin(rad(b[0]))-Math.sin(rad(a[0]))*Math.cos(rad(b[0]))*Math.cos(rad(b[1]-a[1])))*180/Math.PI+360)%360}
(async()=>{
 fs.mkdirSync(out,{recursive:true});
 const proxy=process.env.HTTPS_PROXY||process.env.HTTP_PROXY;
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],...(proxy?{proxy:{server:proxy,bypass:'localhost,127.0.0.1'}}:{})});
 const report={observedAt:new Date().toISOString(),sourceCommit:require('node:child_process').execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),harnessSha256:crypto.createHash('sha256').update(fs.readFileSync(__filename)).digest('hex'),roadTilesSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(site,'road-tiles.js'))).digest('hex'),navigationSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(site,'navigation.js'))).digest('hex'),engineSha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(site,'engine.js'))).digest('hex'),siteManifest:fs.existsSync(path.join(site,'audit-build.json'))?JSON.parse(fs.readFileSync(path.join(site,'audit-build.json'),'utf8')):null,realtime,paceMetersPerSecond:realtime?pace:5,startedAt:new Date().toISOString(),scope:(realtime?'Paced browser GPS every wall-clock second, continuous UI/map rendering. Provider queries and visible tiles are live. ':'Accelerated tracker replay with bounded map checkpoints. ')+'Selected urban, mountain and high-latitude routes, six inhabited continents; live providers, UI coordinate route selection, synthetic GPS. Shared tracker processes every supplied position. Destination selection is recorded per case: an explicit coordinate or an actual returned business. No exhaustive business catalog, real GPS, traffic or real driving verification.',scenarios:[]};
 const save=()=>{fs.writeFileSync(path.join(out,'results.json.tmp'),JSON.stringify(report,null,2));fs.renameSync(path.join(out,'results.json.tmp'),path.join(out,'results.json'))};
 try{for(const s of scenarios.filter(s=>!process.env.CABIN_WORLD_CASES||process.env.CABIN_WORLD_CASES.split(',').includes(s.id))){
  console.log('START',s.id);const dir=path.join(out,s.id);fs.mkdirSync(dir,{recursive:true});
  const context=await browser.newContext({viewport:{width:1440,height:1050},...(process.env.CABIN_WORLD_RECORD?{recordVideo:{dir,size:{width:1440,height:1050}}}:{})}),page=await context.newPage();
  const row={...s,result:'running',startedAt:new Date().toISOString(),search:{},responses:[],requestFailures:[],pageErrors:[],checkpoints:[],samples:[],mapCoverageGaps:[]};report.scenarios.push(row);const pending=[];let route;
  page.on('pageerror',e=>row.pageErrors.push(e.message));
  page.on('requestfailed',r=>{if(/^https:/.test(r.url()))row.requestFailures.push({url:r.url(),error:r.failure()?.errorText})});
  page.on('response',r=>{if(/^https:\/\/(tile\.openstreetmap\.org|nominatim\.openstreetmap\.org|router\.project-osrm\.org)\//.test(r.url())){row.responses.push({url:r.url(),status:r.status()});if(r.url().includes('/route/v1/'))pending.push(r.json().then(j=>{route=j;fs.writeFileSync(path.join(dir,'route.json'),JSON.stringify(j))}).catch(()=>{}));if(r.url().includes('/search?'))pending.push(r.json().then(j=>{row.search.providerResults=j;fs.writeFileSync(path.join(dir,'search.json'),JSON.stringify(j))}).catch(()=>{}));}});
  await page.addInitScript(start=>{const now=Date.now.bind(Date);window.worldOffset=0;Date.now=()=>now()+worldOffset;window.worldCoords={latitude:start[0],longitude:start[1],speed:0,accuracy:5,heading:null};Object.defineProperty(navigator,'geolocation',{configurable:true,value:{watchPosition(ok){window.worldGpsCallbacks=0;window.worldGPS=()=>{worldGpsCallbacks++;ok({coords:{...worldCoords},timestamp:Date.now()})};worldGPS();window.worldTimer=setInterval(worldGPS,1000);return 1},clearWatch(){clearInterval(worldTimer)}}})},s.start);
  async function search(query){await page.locator('#nav-query').fill(query);await page.locator('#nav-search').click();await page.waitForFunction(()=>!document.querySelector('#nav-search').disabled,null,{timeout:55000});await Promise.all(pending)}
  async function checkpoint(at,label){
   const pausedSpeed=realtime?await page.evaluate(()=>{clearInterval(worldTimer);const speed=worldCoords.speed;worldCoords.speed=0;window.worldCheckTimer=setInterval(()=>worldGPS(),1000);return speed}):null;
   await page.evaluate(label=>{document.querySelector('#world-proof').textContent=label+' · 실제 서버 / 합성 GPS / 구간별 지도 점검';worldGPS();cabinDemo.render()},s.label+' '+label);
   const before=Date.now();let timeout=false;
   try{await page.waitForFunction(()=>{const box=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const r=i.getBoundingClientRect();return r.right>box.left&&r.left<box.right&&r.bottom>box.top&&r.top<box.bottom});return tiles.length>=4&&tiles.every(i=>i.complete&&i.naturalWidth>0&&+getComputedStyle(i).opacity>.95)},null,{timeout:20000})}catch{timeout=true}
   const state=await page.evaluate(()=>{const box=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const r=i.getBoundingClientRect();return r.right>box.left&&r.left<box.right&&r.bottom>box.top&&r.top<box.bottom}),n=JSON.parse(CabinOS.navState());return{visible:tiles.length,decoded:tiles.filter(i=>i.complete&&i.naturalWidth>0).length,instruction:n.instruction,instructionLanguage:n.instructionLanguage,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,planningHidden:document.querySelector('#nav-planning').hidden,dragEnabled:worldMap.dragging.enabled(),zoom:worldMap.getZoom(),centerGap:n.fix?worldMap.getCenter().distanceTo([n.fix.latitude,n.fix.longitude]):null}});
   const capture=`checkpoint-${String(row.checkpoints.length).padStart(2,'0')}.png`;await page.screenshot({path:path.join(dir,capture),fullPage:true});row.checkpoints.push({atMeters:at,label,timeout,waitMs:Date.now()-before,...state,capture});
   console.log('ROAD',s.id,Math.round(at),`${state.decoded}/${state.visible}`,timeout?'TIMEOUT':'READY');
   if(realtime)await page.evaluate(speed=>{clearInterval(worldCheckTimer);worldCoords.speed=speed},pausedSpeed);
  }
  try{
   await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
   await page.evaluate(()=>{const f=L.map;L.map=function(...a){return window.worldMap=f.apply(this,a)}});
   await page.locator('aside [data-page="settings"]').click();await page.locator('[data-palette="sand"]').click();await page.locator('aside [data-page="navigation"]').click();
   await page.evaluate(()=>{const p=document.createElement('p');p.id='world-proof';p.style.cssText='color:#ffcb7a;font-size:14px';p.textContent='전세계 표본 경로 검사 · 실제 지도 / 합성 GPS';document.querySelector('#navigation-panel h2').after(p);window.scrollTo(0,document.querySelector('.device').offsetTop-12)});
   await page.locator('[data-nav="locate"]').click();await page.evaluate(language=>{EchoNavigation.setLanguage(language);cabinDemo.render()},s.language);
   await search(s.query);row.search.uiCount=await page.locator('[data-nav-place]').count();row.search.notice=await page.locator('#nav-notice').textContent();row.search.results=await page.locator('[data-nav-place]').allTextContents();
   row.search.result=row.search.uiCount?'returned-results':Array.isArray(row.search.providerResults)?'empty':'provider-error';
   row.search.outsideExpectedCountry=(Array.isArray(row.search.providerResults)?row.search.providerResults:[]).filter(p=>p.address?.country_code&&p.address.country_code.toUpperCase()!==s.country).map(p=>({name:p.name,address:p.display_name,country:p.address.country_code}));
   row.search.distantResults=(Array.isArray(row.search.providerResults)?row.search.providerResults:[]).filter(p=>distance(s.start,[+p.lat,+p.lon])>50000).map(p=>({name:p.name,address:p.display_name,type:p.type,distanceMeters:distance(s.start,[+p.lat,+p.lon])}));
   row.search.nearestResultMeters=Array.isArray(row.search.providerResults)&&row.search.providerResults.length?Math.min(...row.search.providerResults.map(p=>distance(s.start,[+p.lat,+p.lon]))):null;
   await page.screenshot({path:path.join(dir,'search.png'),fullPage:true});
   if(process.env.CABIN_WORLD_USE_SHOP==='1'){
    const places=await page.evaluate(raw=>JSON.parse(CabinOS.placeResults(JSON.stringify(raw),NaN,NaN,false)),row.search.providerResults||[]);
    const index=places.findIndex(p=>distance(s.start,[p.latitude,p.longitude])>=300&&distance(s.start,[p.latitude,p.longitude])<=20000);
    assert.ok(index>=0,'No returned business within the selected 0.3–20km test range; no fabricated destination');
    const place=places[index],provider=(row.search.providerResults||[]).find(p=>String(p.place_id)===place.id);
    assert.equal(provider?.address?.country_code?.toUpperCase(),s.country,'Selected business country must match the scenario');
    row.coordinateEndpoint=s.end;row.end=[place.latitude,place.longitude];row.selectedPlace=place;row.destinationMode='provider-search-result';
    await page.locator(`[data-nav-place="${index}"]`).click();
   }else{row.destinationMode='explicit-coordinate';await search(s.end.join(','));await page.locator('[data-nav-place="0"]').click()}
   await page.waitForSelector('[data-nav="start"]',{timeout:40000});await Promise.all(pending);assert.equal(route?.code,'Ok');
   const r=route.routes[0],points=r.geometry.coordinates.map(p=>[p[1],p[0]]),cum=[0];for(let i=1;i<points.length;i++)cum.push(cum.at(-1)+distance(points[i-1],points[i]));const total=cum.at(-1);
   const steps=r.legs.flatMap(l=>l.steps).map(step=>{let closest=0,gap=Infinity;for(let i=0;i<points.length;i++){const d=distance(points[i],[step.maneuver.location[1],step.maneuver.location[0]]);if(d<gap){closest=i;gap=d}}return{atMeters:cum[closest],type:step.maneuver.type,modifier:step.maneuver.modifier,road:step.name,destinations:step.destinations,drivingSide:step.driving_side,exit:step.maneuver.exit}});
   row.route={distanceMeters:r.distance,geometryMeters:total,durationSeconds:r.duration,points:points.length,steps,originSnapMeters:distance(s.start,points[0]),destinationSnapMeters:distance(row.end,points.at(-1))};
   assert.ok(total>100&&total<100000,'Selected test route must be 0.1–100km');
   const stops=[0,total*.25,total*.5,total*.75];for(const step of steps.filter(x=>['roundabout','rotary','fork','off ramp','on ramp'].includes(x.type)).slice(0,3))stops.push(step.atMeters);
   stops.sort((a,b)=>a-b);const checks=stops.filter((v,i)=>!i||v-stops[i-1]>100);if(total-checks.at(-1)>80)checks.push(total-50);checks.push(total);
   await page.evaluate(p=>{Object.assign(worldCoords,{latitude:p[0],longitude:p[1]});worldGPS();worldMap.stop();worldMap.setZoom(16,{animate:false})},points[0]);
   await page.waitForTimeout(400);
   if(await page.locator('#nav-access-confirm').count())await page.locator('#nav-access-confirm').check();await page.locator('[data-nav="start"]').click();await page.waitForFunction(()=>JSON.parse(CabinOS.navState()).active);await checkpoint(0,'출발');
   if(realtime)await page.evaluate(()=>clearInterval(worldTimer));
   let incompleteMapSince=null,incompleteMapReported=false;
   const drivingStarted=Date.now();row.drivingStartedAt=new Date(drivingStarted).toISOString();let lastBeat=drivingStarted;save();
   let at=0,segment=0;
   for(const target of checks.slice(1)){
    const batch=[];while(at<target){at=Math.min(target,at+(realtime&&total-at>=65?pace:5));while(segment<points.length-2&&cum[segment+1]<at)segment++;const length=cum[segment+1]-cum[segment],t=length?(at-cum[segment])/length:0;batch.push({at,p:[points[segment][0]+(points[segment+1][0]-points[segment][0])*t,interpolateLongitude(points[segment][1],points[segment+1][1],t)],heading:heading(points[segment],points[segment+1])})}
    const samples=[];
    if(realtime){for(const sample of batch){
     if(Date.now()>=deadline){const e=new Error('Configured wall-clock audit deadline reached; route intentionally incomplete');e.code='AUDIT_DEADLINE';throw e}
     await page.waitForTimeout(Math.max(0,1000-(Date.now()-lastBeat)));lastBeat=Date.now();
     const record=await page.evaluate(({s,slow,pace})=>{Object.assign(worldCoords,{latitude:s.p[0],longitude:s.p[1],speed:slow?5:pace,heading:s.heading});const before=performance.now();worldGPS();cabinDemo.render();const n=JSON.parse(CabinOS.navState()),box=document.querySelector('#navigation-map').getBoundingClientRect(),tiles=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const r=i.getBoundingClientRect();return r.right>box.left&&r.left<box.right&&r.bottom>box.top&&r.top<box.bottom});return{atMeters:s.at,observedAt:new Date().toISOString(),map:{visible:tiles.length,decoded:tiles.filter(i=>i.complete&&i.naturalWidth>0).length,status:document.querySelector('#nav-map-status').textContent},remainingMeters:n.remainingMeters,instruction:n.instruction,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,updateMs:performance.now()-before}}, {s:sample,slow:total-sample.at<65,pace});
     samples.push(record);row.samples.push(record);row.drivingWallSeconds=(Date.now()-drivingStarted)/1000;
     if(record.map.visible>0&&record.map.decoded===record.map.visible){incompleteMapSince=null;incompleteMapReported=false}else{incompleteMapSince??=Date.now();if(Date.now()-incompleteMapSince>=15000&&!incompleteMapReported){incompleteMapReported=true;row.mapCoverageGaps.push({atMeters:record.atMeters,observedAt:record.observedAt,...record.map});await page.screenshot({path:path.join(dir,`map-gap-${row.mapCoverageGaps.length}.png`),fullPage:true})}}
     fs.appendFileSync(path.join(dir,'samples.jsonl'),JSON.stringify(record)+'\n');
     assert.equal(record.tracking,true,`Lost tracking at ${record.atMeters}`);assert.equal(record.offRoute,false,`False off-route at ${record.atMeters}`);
     if(row.samples.length%30===0){save();console.log('HEARTBEAT',s.id,Math.round(record.atMeters),Math.round(row.drivingWallSeconds)+'s')}
    }}else{samples.push(...await page.evaluate(batch=>batch.map(s=>{worldOffset+=1000;Object.assign(worldCoords,{latitude:s.p[0],longitude:s.p[1],speed:5,heading:s.heading});const before=performance.now();CabinOS.navFix(s.p[0],s.p[1],5,Date.now(),5,s.heading);const n=JSON.parse(CabinOS.navState());return{atMeters:s.at,remainingMeters:n.remainingMeters,instruction:n.instruction,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,updateMs:performance.now()-before}}),batch));row.samples.push(...samples)}
    for(const sample of samples){assert.equal(sample.tracking,true,`Lost tracking at ${sample.atMeters}`);assert.equal(sample.offRoute,false,`False off-route at ${sample.atMeters}`);assert.ok(Math.abs(sample.remainingMeters-r.distance*(1-sample.atMeters/total))<80,`Wrong progress at ${sample.atMeters}`)}
    await checkpoint(target,target===total?'도착':`${Math.round(target)}m`);const c=row.checkpoints.at(-1);assert.equal(c.planningHidden,true);assert.equal(c.dragEnabled,false);if(!c.arrived)assert.ok(c.centerGap<10,'Active map must follow GPS');else assert.ok(c.centerGap<80,'Arrival must remain near the visible vehicle position');assert.ok(await page.locator('#nav-symbol svg path,#nav-symbol svg circle').count(),'Maneuver icon must render without font dependency');
   }
   if(realtime)await page.waitForTimeout(1100);
   await page.evaluate(realtime=>{if(!realtime)worldOffset+=1000;worldCoords.speed=0;worldGPS();cabinDemo.render()},realtime);row.finalState=await page.evaluate(()=>JSON.parse(CabinOS.navState()));assert.equal(row.finalState.arrived,true);assert.equal(await page.locator('#nav-remaining').evaluate(e=>e.closest('p').hidden),true);
   // Genuine parked-state debounce before enabling zoom; never bypass driving controls.
   for(let i=0;i<6;i++){await page.waitForTimeout(1000);await page.evaluate(()=>worldGPS())}await page.evaluate(()=>{CabinOS.vehicle('주차');cabinDemo.render()});await page.waitForFunction(()=>cabinDemo.state.parked);
   const parkedZoom=await page.evaluate(()=>worldMap.getZoom());row.parkedZoom=parkedZoom;
   await page.locator('#navigation-map .leaflet-control-zoom-in').click();await page.waitForFunction(z=>worldMap.getZoom()===z+1,parkedZoom);await checkpoint(total,'주차 후 확대');
   await page.locator('#navigation-map').screenshot({path:path.join(dir,'map-detail.png')});
   await page.locator('#navigation-map .leaflet-control-zoom-out').click();await page.waitForFunction(z=>worldMap.getZoom()===z,parkedZoom);row.zoomInOutVerified=true;
   assert.ok(row.checkpoints.every(c=>!c.timeout&&c.visible===c.decoded),'One or more viewport tile checks failed');assert.deepEqual(row.pageErrors,[]);assert.equal(row.mapCoverageGaps.length,0,'Visible road tiles remained incomplete for at least 15 seconds during movement');row.result='passed';console.log('PASS',s.id,Math.round(r.distance),'meters',row.samples.length,'fixes',row.search.result);
  }catch(e){row.result=e.code==='AUDIT_DEADLINE'?'deadline-incomplete':'failed';row.failure=e.message;row.failureStack=e.stack;row.notice=await page.locator('#nav-notice').textContent().catch(()=>'');await page.screenshot({path:path.join(dir,'failure.png'),fullPage:true}).catch(()=>{});console.error('FAIL',s.id,e.message);process.exitCode=1}
  finally{row.gpsCallbacks=await page.evaluate(()=>window.worldGpsCallbacks||0).catch(()=>null);row.finishedAt=new Date().toISOString();await Promise.all(pending);const video=page.video();await context.close();if(video)row.video=path.basename(await video.path());save()}
 }}finally{await browser.close();save()}
})().catch(e=>{console.error(e);process.exitCode=1});
