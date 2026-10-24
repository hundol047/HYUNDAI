// Opt-in actual-provider driving rehearsal. Only GPS and its accelerated clock
// are synthetic. No route/search/tile interception; never a physical road test.
const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..'),out=path.resolve(root,process.env.CABIN_DRIVE_OUT||'artifacts/challenging-drive');
fs.mkdirSync(out,{recursive:true});
const scenarios=[
 {id:'seoul',label:'서울 삼청동 이면도로',query:'Blue Bottle Samcheong Seoul',start:[37.5700,126.9795]},
 {id:'paris',label:'파리 개선문 회전교차로',query:'Ladurée Champs Élysées Paris',start:[48.8749,2.2855],requires:'rotary'},
 {id:'sf',label:'샌프란시스코 Lombard 연속 급커브',query:'Caffe Trieste San Francisco',start:[37.80210,-122.4195]}
];
const radians=d=>d*Math.PI/180;
function distance(a,b){const p=radians(b[0]-a[0]),q=radians(b[1]-a[1]);return 6371000*2*Math.asin(Math.sqrt(Math.sin(p/2)**2+Math.cos(radians(a[0]))*Math.cos(radians(b[0]))*Math.sin(q/2)**2))}
function bearing(a,b){const p=radians(a[0]),q=radians(b[0]),l=radians(b[1]-a[1]);return (Math.atan2(Math.sin(l)*Math.cos(q),Math.cos(p)*Math.sin(q)-Math.sin(p)*Math.cos(q)*Math.cos(l))*180/Math.PI+360)%360}
(async()=>{
 const proxy=process.env.HTTPS_PROXY||process.env.HTTP_PROXY;
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],...(proxy?{proxy:{server:proxy,bypass:'127.0.0.1,localhost'}}:{})});
 const report={navigationScriptSha256:require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(root,'build/site/navigation.js'))).digest('hex'),observedAt:new Date().toISOString(),type:'Actual Chromium execution with live OSM tiles, Nominatim businesses, OSRM roads; synthetic 1 Hz GPS clock accelerated approximately 6x, 6 m/s (21.6 km/h). Tile waits use real time.',physicalGps:false,physicalDriving:false,allShopsGuaranteed:false,scenarios:[]};
 try{
  for(const scenario of scenarios){
   const dir=path.join(out,scenario.id);fs.mkdirSync(dir,{recursive:true});
   const context=await browser.newContext({viewport:{width:1440,height:1050},recordVideo:{dir,size:{width:1440,height:1050}}});
   const page=await context.newPage(),row={...scenario,result:'failed',responses:[],requestFailures:[],pageErrors:[],tileChecks:[],samples:[],captures:[]};report.scenarios.push(row);
   let route;
   page.on('pageerror',e=>row.pageErrors.push(e.message));
   page.on('requestfailed',r=>{if(/^https:/.test(r.url()))row.requestFailures.push({host:new URL(r.url()).host,error:r.failure()?.errorText})});
   const pending=[];
   page.on('response',r=>{if(/^https:\/\/(tile\.openstreetmap\.org|nominatim\.openstreetmap\.org|router\.project-osrm\.org)\//.test(r.url())){
    row.responses.push({url:r.url(),status:r.status()});
    if(r.url().includes('/route/v1/'))pending.push(r.json().then(j=>{route=j;fs.writeFileSync(path.join(dir,'route.json'),JSON.stringify(j))}).catch(()=>{}));
    if(r.url().includes('/search?'))pending.push(r.json().then(j=>{row.searchResults=j.map(p=>({name:p.name,address:p.display_name,latitude:+p.lat,longitude:+p.lon}));fs.writeFileSync(path.join(dir,'search.json'),JSON.stringify(j))}).catch(()=>{}));
   }});
   await page.addInitScript(start=>{
    const realNow=Date.now.bind(Date);window.driveClockOffset=0;Date.now=()=>realNow()+window.driveClockOffset;
    window.driveCoords={latitude:start[0],longitude:start[1],accuracy:5,speed:0,heading:null};
    Object.defineProperty(navigator,'geolocation',{configurable:true,value:{watchPosition(success){window.driveGPS=()=>success({coords:{...window.driveCoords},timestamp:Date.now()});driveGPS();window.driveTimer=setInterval(driveGPS,1000);return 1},clearWatch(){clearInterval(window.driveTimer)}}});
   },scenario.start);
   async function tiles(at){const started=Date.now();let timedOut=false;
    try{await page.waitForFunction(()=>{
     const box=document.querySelector('#navigation-map')?.getBoundingClientRect();if(!box)return false;
     const imgs=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const b=i.getBoundingClientRect();return b.right>box.left&&b.left<box.right&&b.bottom>box.top&&b.top<box.bottom});
     return imgs.length>=4&&imgs.every(i=>i.complete&&i.naturalWidth>0&&+getComputedStyle(i).opacity>.95);
    },null,{timeout:25000})}catch{timedOut=true}
    const health=await page.evaluate(()=>{const r=document.querySelector('#navigation-map').getBoundingClientRect(),imgs=[...document.querySelectorAll('#navigation-map .leaflet-tile')].filter(i=>{const b=i.getBoundingClientRect();return b.right>r.left&&b.left<r.right&&b.bottom>r.top&&b.top<r.bottom});return{visible:imgs.length,decoded:imgs.filter(i=>i.complete&&i.naturalWidth>0).length,zoom:driveMap.getZoom(),status:document.querySelector('#nav-map-status').textContent}});
    row.tileChecks.push({atMeters:at,waitMs:Date.now()-started,timedOut,...health});assert.equal(timedOut,false,`${scenario.id}: visible road tile timeout at ${at}m`);
   }
   async function capture(name){const file=`${scenario.id}-${name}.png`;await page.screenshot({path:path.join(out,file),fullPage:true});row.captures.push(file)}
   try{
    await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
    await page.evaluate(()=>{const f=L.map;L.map=function(...a){return window.driveMap=f.apply(this,a)}});
    await page.locator('aside [data-page="settings"]').click();await page.locator('[data-palette="sand"]').click();
    await page.locator('#supplementary').evaluate(e=>e.open=true);await page.locator('aside [data-page="navigation"]').click();
    await page.evaluate(label=>{const p=document.createElement('p');p.id='drive-proof';p.textContent=label+' · 실제 지도·가게·경로 / 합성 GPS 약 6배속 · 실차 주행 아님';p.style.cssText='color:#ffcb7a;font-size:15px';document.querySelector('#navigation-panel h2').after(p)},scenario.label);
    await page.evaluate(()=>window.scrollTo(0,document.querySelector('.device').offsetTop-12));
    await page.locator('[data-nav="locate"]').click();await page.locator('#nav-query').fill(scenario.query);await page.locator('#nav-search').click();
    await page.waitForSelector('[data-nav-place="0"]',{timeout:35000});await page.locator('[data-nav-place="0"]').click();await page.waitForSelector('[data-nav="start"]',{timeout:35000});await Promise.all(pending);
    assert.equal(route?.code,'Ok');const r=route.routes[0],points=r.geometry.coordinates.map(p=>[p[1],p[0]]),cumulative=[0];for(let i=1;i<points.length;i++)cumulative.push(cumulative.at(-1)+distance(points[i-1],points[i]));
    const maneuvers=r.legs.flatMap(l=>l.steps).map(s=>({type:s.maneuver.type,modifier:s.maneuver.modifier,road:s.name,exit:s.maneuver.exit,location:s.maneuver.location}));
    if(scenario.requires)assert.ok(maneuvers.some(m=>m.type===scenario.requires),'Required difficult road feature absent');
    let initial250mHeadingChangeDegrees=0;const bends=[];for(let i=1;i<points.length-1;i++){const angle=Math.abs(((bearing(points[i],points[i+1])-bearing(points[i-1],points[i])+540)%360)-180);if(cumulative[i]<=250)initial250mHeadingChangeDegrees+=angle;if(angle>=55)bends.push({atMeters:+cumulative[i].toFixed(1),angle:+angle.toFixed(1)})}
    row.route={distanceMeters:r.distance,geometryMeters:cumulative.at(-1),points:points.length,maneuvers,bends,initial250mHeadingChangeDegrees};
    // Start exactly at the provider's road origin, explicitly a synthetic test input.
    await page.evaluate(p=>{driveCoords.latitude=p[0];driveCoords.longitude=p[1];driveGPS();driveMap.setZoom(17,{animate:false})},points[0]);
    await tiles(0);await capture('preview');
    if(await page.locator('#nav-access-confirm').count())await page.locator('#nav-access-confirm').check();
    await page.locator('[data-nav="start"]').click();await page.waitForFunction(()=>JSON.parse(CabinOS.navState()).active);
    let segment=0,checkpoint=0;const total=cumulative.at(-1),instructions=new Set(),symbols=new Set();
    for(let at=0;at<=total+6;at+=6){
     const position=Math.min(at,total);while(segment<points.length-2&&cumulative[segment+1]<position)segment++;
     const len=cumulative[segment+1]-cumulative[segment],t=len?(position-cumulative[segment])/len:0;
     const p=[points[segment][0]+(points[segment+1][0]-points[segment][0])*t,points[segment][1]+(points[segment+1][1]-points[segment][1])*t];
     const sample=await page.evaluate(({p,heading,at})=>{window.driveClockOffset+=834;Object.assign(driveCoords,{latitude:p[0],longitude:p[1],speed:at===0?0:6,heading});const started=performance.now();driveGPS();cabinDemo.render();const n=JSON.parse(CabinOS.navState());return{atMeters:at,fix:n.fix,tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,active:n.active,remainingMeters:n.remainingMeters,instruction:n.instruction,turnMeters:n.turnMeters,updateMs:performance.now()-started,planningHidden:document.querySelector('#nav-planning').hidden,dragEnabled:driveMap.dragging.enabled(),centerDistance:L.latLng(p).distanceTo(driveMap.getCenter())}},{p,heading:bearing(points[segment],points[segment+1]),at:position});
     row.samples.push(sample);instructions.add(sample.instruction);
     const icon=await page.locator('#nav-symbol').getAttribute('data-symbol');symbols.add(icon);
     assert.ok(await page.locator('#nav-symbol svg path,#nav-symbol svg circle').count(),'Maneuver must be a font-independent vector');
     assert.equal(sample.tracking,true,`${scenario.id}: lost tracking at ${position}m`);assert.equal(sample.offRoute,false,`${scenario.id}: false off-route at ${position}m`);
     assert.equal(sample.planningHidden,true);assert.equal(sample.dragEnabled,false);assert.ok(sample.centerDistance<10,'Map must follow the received road position');
     const expected=(1-position/total)*r.distance;assert.ok(Math.abs(sample.remainingMeters-expected)<70,`${scenario.id}: wrong road progress at ${position}m (${sample.remainingMeters} vs ${expected})`);
     if(position>=checkpoint||at>=total){await tiles(position);checkpoint=position+120}
     if(at===0||position>=total*.48&&position<total*.48+6||at>=total)await capture(at===0?'start':at>=total?'arrival':'middle');
     if(scenario.id==='sf'&&position>=90&&position<96)await capture('hairpins');
     if(sample.arrived){row.arrivalAtMeters=position;break}
     await page.waitForTimeout(166);
    }
    if(!row.samples.at(-1).arrived){await page.waitForTimeout(1000);await page.evaluate(()=>driveGPS());row.arrivalFinalState=await page.evaluate(()=>JSON.parse(CabinOS.navState()));assert.equal(row.arrivalFinalState.arrived,true)}
    await tiles(total);await capture('finish');
    row.instructions=[...instructions];row.vectorSymbols=[...symbols];row.falseOffRoutes=row.samples.filter(s=>s.offRoute).length;row.lostTracking=row.samples.filter(s=>!s.tracking).length;
    row.updateP95Ms=[...row.samples].map(s=>s.updateMs).sort((a,b)=>a-b)[Math.floor(row.samples.length*.95)];
    assert.deepEqual(row.pageErrors,[]);row.result='passed';console.log('PASS',scenario.id,JSON.stringify({distance:r.distance,samples:row.samples.length,tiles:row.tileChecks.length,instructions:row.instructions.length,p95:row.updateP95Ms}));
   }catch(e){row.failure=e.message;row.notice=await page.locator('#nav-notice').textContent().catch(()=>'');await capture('failure').catch(()=>{});console.error('FAIL',scenario.id,e.message);process.exitCode=1}
   finally{await Promise.all(pending);const video=page.video();await context.close();row.video=path.relative(out,await video.path());fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2))}
  }
 }finally{await browser.close();fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2))}
})().catch(e=>{console.error(e);process.exitCode=1});
