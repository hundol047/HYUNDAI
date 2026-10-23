// Chromium visible-tile lifecycle tests. PNG fixtures are health inputs, not proof of real roads.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'artifacts/road-tiles-qa');fs.mkdirSync(out,{recursive:true});
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||['/usr/bin/chromium','/usr/bin/google-chrome'].find(fs.existsSync),headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:1440,height:1120}}),results=[],errors=[];page.on('pageerror',e=>errors.push(e.message));let mode='good';await page.clock.install();
 await page.route('https://tile.openstreetmap.org/**',r=>mode==='good'?r.fulfill({status:200,contentType:'image/png',body:png}):r.fulfill({status:503,body:'Unavailable'}));
 async function fresh(next='good'){
  mode=next;await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>cabinDemo?.state.parked);
  await page.evaluate(()=>{const original=L.map;L.map=function(...args){const m=original.apply(this,args);if(args[0]==='navigation-map')window.roadMap=m;return m};Object.defineProperty(navigator,'geolocation',{configurable:true,value:{watchPosition(fn){fn({coords:{latitude:37.5547,longitude:126.9723,accuracy:5,speed:0},timestamp:Date.now()});return 1},clearWatch(){}}})});
  await page.locator('aside [data-page="navigation"]').click();
 }
 async function ready(){await page.waitForFunction(()=>document.querySelector('#nav-road-health').hidden)}
 async function timeout(){await page.clock.fastForward(16000);await page.waitForFunction(()=>document.querySelector('#nav-map-status').textContent.includes('불러오지 못'));await page.waitForFunction(()=>cabinDemo.state.parked)}
 async function test(name,body){try{await body();results.push({name,result:'passed'});console.log('PASS',name)}catch(e){results.push({name,result:'failed',error:e.message});throw e}}
 try{
  await test('all visible decoded bitmap tiles are required before the map reports ready',async()=>{await fresh();await ready();assert.match(await page.locator('#nav-map-status').textContent(),/표시 중/);assert.ok(await page.evaluate(()=>[...document.querySelectorAll('#navigation-map img.leaflet-tile')].filter(i=>i.naturalWidth>0).length>=4))});
  await test('failed basemap is explicit and GPS success cannot disguise an empty map',async()=>{await fresh('bad');await page.locator('[data-nav="locate"]').click();assert.equal(await page.evaluate(()=>JSON.parse(CabinOS.navState()).gpsReady),true);await timeout();assert.equal(await page.locator('#nav-road-health').isVisible(),true);assert.equal(await page.locator('#nav-tiles-retry').isVisible(),true)});
  await test('tile retry recovers roads without clearing the entered destination',async()=>{await page.locator('#nav-query').fill('서울역');mode='good';await page.locator('#nav-tiles-retry').click();await ready();assert.equal(await page.locator('#nav-query').inputValue(),'서울역')});
  await test('old viewport tiles cannot mark a newly failed viewport as ready',async()=>{await fresh();await ready();mode='bad';await page.evaluate(()=>roadMap.setView([48.85,2.35],16,{animate:false}));await timeout();assert.equal(await page.locator('#nav-road-health').isVisible(),true)});
  await test('continuous GPS-like map movement cannot postpone a persistent tile failure forever',async()=>{await fresh('bad');for(let i=0;i<17;i++){await page.evaluate(()=>roadMap.panBy([3,0],{animate:false}));await page.clock.fastForward(1000)}assert.match(await page.locator('#nav-map-status').textContent(),/불러오지 못/);mode='good';await page.locator('#nav-tiles-retry').click();await ready();assert.equal(await page.locator('#nav-road-health').isVisible(),false)});
  await test('offline state retains already displayed roads and labels its limitation',async()=>{await fresh();await ready();const before=await page.locator('#navigation-map .leaflet-tile-loaded').count();await page.evaluate(()=>{CabinOS.network(false);cabinDemo.render()});assert.match(await page.locator('#nav-map-status').textContent(),/오프라인.*받아 둔/);assert.equal(await page.locator('#navigation-map .leaflet-tile-loaded').count(),before)});
  await test('leaving and reopening the map retires old health callbacks',async()=>{await fresh('bad');await page.locator('aside [data-page="home"]').click();mode='good';await page.locator('aside [data-page="navigation"]').click();await ready();await page.waitForTimeout(1200);assert.equal(await page.locator('#nav-road-health').isVisible(),false)});
  await test('failed-map help and retry fit a narrow mobile viewport',async()=>{await fresh('bad');await page.setViewportSize({width:390,height:844});await timeout();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:path.join(out,'map-recovery-mobile.png'),fullPage:true})});
  assert.deepEqual(errors,[]);
 }finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({type:'actual Chromium lifecycle execution with explicit synthetic bitmap/provider/GPS inputs; real-road rendering is verified separately',results,pageErrors:errors},null,2));await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
