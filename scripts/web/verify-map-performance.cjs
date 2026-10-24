// Deterministic rendering/workload checks; network latency is measured separately.
const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..'),baseline=process.argv.includes('--baseline');
const out=path.join(root,'artifacts/map-performance');fs.mkdirSync(out,{recursive:true});
const cpu=Number(process.env.CABIN_CPU_RATE||4);
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||['/usr/bin/chromium','/usr/bin/google-chrome'].find(fs.existsSync),headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:1440,height:1120}}),errors=[],checks=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('https://tile.openstreetmap.org/**',r=>r.abort());
 const cdp=await page.context().newCDPSession(page);await cdp.send('Emulation.setCPUThrottlingRate',{rate:cpu});
 let metrics={};
 try {
  await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
  await page.evaluate(()=>{
   const original=L.map;window.qaMaps={};window.qaMapCounts={center:0,fit:0};
   L.map=function(id,...args){const map=original.call(this,id,...args);window.qaMaps[typeof id==='string'?id:id.id]=map;
    for(const [method,key] of [['setView','center'],['fitBounds','fit']]){const f=map[method];map[method]=function(...a){window.qaMapCounts[key]++;return f.apply(this,a)}}return map};
   const RO=window.ResizeObserver;window.qaActiveObservers=0;
   window.ResizeObserver=class extends RO {constructor(cb){super(cb);this.qaActive=true;window.qaActiveObservers++}disconnect(){if(this.qaActive){window.qaActiveObservers--;this.qaActive=false}super.disconnect()}};
  });
  async function test(name,body){try{await body();checks.push({name,result:'passed'});console.log('PASS',name)}catch(e){checks.push({name,result:'failed',error:e.message});if(!baseline)throw e;console.log('BASELINE ISSUE',name,e.message)}}
  await page.locator('aside [data-page="nearby"]').click();await page.waitForTimeout(500);
  await test('nearby zoom buttons, mouse wheel and drag pan work while parked',async()=>{
   const z=await page.evaluate(()=>qaMaps['nearby-map'].getZoom());await page.locator('#nearby-map .leaflet-control-zoom-in').click();await page.waitForTimeout(400);assert.equal(await page.evaluate(()=>qaMaps['nearby-map'].getZoom()),z+1);
   await page.locator('#nearby-map .leaflet-control-zoom-out').click();await page.waitForTimeout(400);assert.equal(await page.evaluate(()=>qaMaps['nearby-map'].getZoom()),z);
   await page.locator('#nearby-map').scrollIntoViewIfNeeded();const box=await page.locator('#nearby-map').boundingBox();await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.wheel(0,-500);await page.waitForTimeout(600);assert.ok(await page.evaluate(z=>qaMaps['nearby-map'].getZoom()>z,z));
   const before=await page.evaluate(()=>qaMaps['nearby-map'].getCenter());await page.mouse.down();await page.mouse.move(box.x+box.width/2+90,box.y+box.height/2+40,{steps:12});await page.mouse.up();await page.waitForTimeout(600);assert.ok(await page.evaluate(p=>qaMaps['nearby-map'].getCenter().distanceTo(p)>1,before));
  });
  await test('chosen nearby zoom and center survive timer updates and resize',async()=>{
   await page.evaluate(()=>qaMaps['nearby-map'].setView([-33.8688,151.2093],8,{animate:false}));const before=await page.evaluate(()=>({center:qaMaps['nearby-map'].getCenter(),zoom:qaMaps['nearby-map'].getZoom()}));
   await page.setViewportSize({width:1400,height:1120});await page.waitForTimeout(500);await page.evaluate(()=>cabinDemo.render());
   const after=await page.evaluate(()=>({center:qaMaps['nearby-map'].getCenter(),zoom:qaMaps['nearby-map'].getZoom()}));assert.equal(after.zoom,before.zoom);assert.ok(await page.evaluate(({a,b,z})=>{const m=qaMaps['nearby-map'];return m.project(a,z).distanceTo(m.project(b,z))<=1},{a:before.center,b:after.center,z:before.zoom}),'resize must preserve center within one display pixel');
  });
  await test('navigation zoom and pan survive updates without changing route coordinates',async()=>{
   await page.locator('#supplementary').evaluate(e=>e.open=true);await page.locator('aside [data-page="navigation"]').click();await page.waitForTimeout(350);const m='#navigation-map';await page.locator(m).scrollIntoViewIfNeeded();
   await page.locator(m+' .leaflet-control-zoom-in').click();await page.waitForTimeout(350);assert.equal(await page.evaluate(()=>qaMaps['navigation-map'].getZoom()),3);
   await page.locator(m+' .leaflet-control-zoom-out').click();await page.waitForTimeout(350);assert.equal(await page.evaluate(()=>qaMaps['navigation-map'].getZoom()),2);
   const b=await page.locator(m).boundingBox();await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await page.mouse.wheel(0,-500);await page.waitForTimeout(500);assert.ok(await page.evaluate(()=>qaMaps['navigation-map'].getZoom()>2));
   await page.mouse.down();await page.mouse.move(b.x+b.width/2+80,b.y+b.height/2,{steps:10});await page.mouse.up();await page.waitForTimeout(500);const before=await page.evaluate(()=>qaMaps['navigation-map'].getCenter());await page.evaluate(()=>cabinDemo.render());assert.ok(await page.evaluate(p=>qaMaps['navigation-map'].getCenter().distanceTo(p)<1,before));
  });
  const setup=await page.evaluate(()=>{
   const points=Array.from({length:100000},(_,i)=>[126.9723+i*.000002,37.5547]);window.qaRoutePoints=points;
   const json=JSON.stringify({code:'Ok',routes:[{distance:18000,duration:1200,geometry:{coordinates:points},legs:[{steps:[{maneuver:{type:'depart',location:points[0]}},{maneuver:{type:'arrive',location:points.at(-1)}}]}]}]});
   CabinOS.navFix(37.5547,126.9723,5,Date.now(),0,-1);const at=performance.now();const error=CabinOS.navPreview(json,'Large route stress fixture',37.5547,points.at(-1)[0]);if(error)throw Error(error);cabinDemo.render();return {points:points.length,parseAndInitialRenderMs:performance.now()-at};
  });
  await test('preview zoom is preserved and guidance starts with driving controls locked',async()=>{
   await page.evaluate(()=>qaMaps['navigation-map'].setZoom(10,{animate:false}));await page.waitForTimeout(100);await page.evaluate(()=>cabinDemo.render());assert.equal(await page.evaluate(()=>qaMaps['navigation-map'].getZoom()),10);
   // A heavy route/zoom can expire the simulated vehicle sample. Wait for
   // normal P debounce again; GPS alone must never restore parking trust.
   await page.waitForFunction(()=>window.cabinDemo.state.parked);
   const start=await page.evaluate(()=>{CabinOS.navFix(37.5547,126.9723,5,Date.now(),0,-1);const ok=CabinOS.navStart(false);return {ok,nav:JSON.parse(CabinOS.navState()),parked:cabinDemo.state.parked,now:Date.now()}});assert.equal(start.ok,true,JSON.stringify(start));await page.evaluate(()=>cabinDemo.render());assert.equal(await page.locator('#navigation-map .leaflet-control-zoom').count(),0);assert.equal(await page.evaluate(()=>qaMaps['navigation-map'].dragging.enabled()),false);
  });
  const idle=await page.evaluate(async()=>{
   qaMapCounts.center=0;let mutations=0;const target=document.querySelector('#nav-guidance'),stop=target.querySelector('[data-nav="stop"]');const observer=new MutationObserver(x=>mutations+=x.filter(r=>r.type==='childList').length);observer.observe(target,{childList:true,subtree:true});
   for(let i=0;i<25;i++){cabinDemo.render();await new Promise(requestAnimationFrame)}observer.disconnect();return {recenterCalls:qaMapCounts.center,guidanceChildListMutations:mutations,stopButtonPreserved:stop===target.querySelector('[data-nav="stop"]')};
  });
  await test('idle navigation preserves controls and does not repeatedly recenter map',async()=>{assert.equal(idle.recenterCalls,0);assert.equal(idle.stopButtonPreserved,true)});
  const movement=await page.evaluate(async()=>{
   const samples=[],frames=[];let previous=performance.now();const stop=document.querySelector('[data-nav="stop"]');qaMapCounts.center=0;
   for(let i=1;i<=120;i++){
    await new Promise(requestAnimationFrame);const now=performance.now();frames.push(now-previous);previous=now;
    const start=performance.now();CabinOS.navFix(37.5547,126.9723+i*.000003,5,Date.now(),2,90);cabinDemo.render();samples.push(performance.now()-start);
   }
   return {samplesMs:samples,animationFrameIntervalsMs:frames,recenterCalls:qaMapCounts.center,stopButtonPreserved:stop===document.querySelector('[data-nav="stop"]'),tracking:JSON.parse(CabinOS.navState()).tracking};
  });
  const percentile=(a,p)=>[...a].sort((x,y)=>x-y)[Math.min(a.length-1,Math.floor(a.length*p))];
  metrics={...setup,cpuThrottleRate:cpu,idle,moving:{fixes:movement.samplesMs.length,p50Ms:percentile(movement.samplesMs,.5),p95Ms:percentile(movement.samplesMs,.95),maxMs:Math.max(...movement.samplesMs),rafIntervalP95Ms:percentile(movement.animationFrameIntervalsMs,.95),recenterCalls:movement.recenterCalls,stopButtonPreserved:movement.stopButtonPreserved,tracking:movement.tracking}};
  await test('100000-point route remains responsive and keeps the stop control stable during GPS updates',async()=>{assert.equal(movement.tracking,true);assert.equal(movement.stopButtonPreserved,true);assert.ok(metrics.moving.p95Ms<100,`GPS + render p95 ${metrics.moving.p95Ms.toFixed(1)}ms exceeds 100ms`)});
  await test('screen changes release map ResizeObservers',async()=>{
   // Release the navigation safety latch only through explicit demo P reconfirmation.
   await page.evaluate(()=>{CabinOS.navStop();CabinOS.navFix(37.5547,126.9723,5,Date.now(),0,-1)});for(let i=0;i<6;i++){await page.waitForTimeout(1000);await page.evaluate(()=>CabinOS.navFix(37.5547,126.9723,5,Date.now(),0,-1))}await page.evaluate(()=>{CabinOS.vehicle('주차');cabinDemo.render()});await page.waitForFunction(()=>cabinDemo.state.parked);
   for(let i=0;i<8;i++){await page.locator('#supplementary').evaluate(e=>e.open=true);await page.locator('aside [data-page="navigation"]').click();await page.locator('aside [data-page="home"]').click()}
   assert.equal(await page.evaluate(()=>qaActiveObservers),0);
  });
  await test('two finger pinch zooms in and out on nearby and navigation touch maps',async()=>{
   const touch=await browser.newPage({viewport:{width:820,height:1180},hasTouch:true});touch.on('pageerror',e=>errors.push(e.message));
   try {
    await touch.route('https://tile.openstreetmap.org/**',r=>r.abort());await touch.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await touch.waitForFunction(()=>window.cabinDemo?.state.parked);
    await touch.evaluate(()=>{const original=L.map;L.map=function(...a){return window.touchMap=original.apply(this,a)}});
    const session=await touch.context().newCDPSession(touch);
    for(const name of ['nearby','navigation']) {
     await touch.locator(`aside [data-page="${name}"]`).click();const target=touch.locator(`#${name}-map`);await target.scrollIntoViewIfNeeded();await touch.waitForTimeout(350);
     const box=await target.boundingBox(),x=box.x+box.width/2,y=box.y+box.height/2;
     async function pinch(from,to){await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:x-from,y,id:1},{x:x+from,y,id:2}]});for(let i=1;i<=8;i++){const gap=from+(to-from)*i/8;await session.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x-gap,y,id:1},{x:x+gap,y,id:2}]});await touch.waitForTimeout(20)}await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await touch.waitForTimeout(500)}
     const before=await touch.evaluate(()=>touchMap.getZoom());await pinch(25,95);const expanded=await touch.evaluate(()=>touchMap.getZoom());assert.ok(expanded>before,`${name}: pinch must zoom in`);await pinch(95,25);assert.ok(await touch.evaluate(z=>touchMap.getZoom()<z,expanded),`${name}: pinch must zoom out`);
    }
   }finally{await touch.close()}
  });
  assert.deepEqual(errors,[]);
 }finally{
  const report={type:'headless Chromium deterministic map/GPS stress workload; synthetic 100000-point route, fixture GPS, aborted tile requests; not device FPS or live network latency',timestamp:new Date().toISOString(),baseline,metrics,checks,pageErrors:errors};
  fs.writeFileSync(path.join(out,baseline?'baseline.json':'results.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(metrics));await browser.close();
 }
})().catch(e=>{console.error(e);process.exitCode=1});
