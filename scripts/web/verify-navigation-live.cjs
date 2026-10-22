const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'artifacts/navigation-live-ui');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const proxy=process.env.HTTPS_PROXY||process.env.HTTP_PROXY;
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||['/usr/bin/chromium','/usr/bin/google-chrome'].find(x=>fs.existsSync(x)),headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],...(proxy?{proxy:{server:proxy,bypass:'127.0.0.1,localhost'}}:{})});
 const page=await browser.newPage({viewport:{width:1600,height:1100}}),responses=[],errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{if(/^https:\/\/(tile\.openstreetmap\.org|nominatim\.openstreetmap\.org|router\.project-osrm\.org)\//.test(r.url()))responses.push({url:r.url(),status:r.status()})});
 // Only device position is synthetic. No provider route interception or mocked network response.
 await page.addInitScript(()=>{
  let timer;
  const coords={latitude:37.5547,longitude:126.9723,accuracy:5,speed:0,heading:null};
  window.liveTestCoords=coords;
  Object.defineProperty(navigator,'geolocation',{configurable:true,value:{
   watchPosition(success){success({coords:{...coords},timestamp:Date.now()});timer=setInterval(()=>success({coords:{...coords},timestamp:Date.now()}),1000);return 1},
   clearWatch(){clearInterval(timer)},
   getCurrentPosition(success){success({coords:{...coords},timestamp:Date.now()})}
  }});
 });
 async function tilesReady(){await page.waitForFunction(()=>{const map=document.querySelector('#navigation-map');if(!map)return false;const r=map.getBoundingClientRect();const tiles=[...map.querySelectorAll('.leaflet-tile')].filter(i=>{const b=i.getBoundingClientRect();return b.right>r.left&&b.left<r.right&&b.bottom>r.top&&b.top<r.bottom});return tiles.length>=4&&tiles.every(i=>i.complete&&i.naturalWidth>0&&+getComputedStyle(i).opacity>.95)},null,{timeout:30000})}
 let result='failed',failure='';
 try{
  await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>window.cabinDemo?.state.parked);
  await page.locator('aside [data-page="settings"]').click();await page.locator('[data-palette="sand"]').click();
  await page.locator('aside [data-page="navigation"]').click();await page.locator('[data-nav="locate"]').click();
  await page.locator('#nav-query').fill('Seoul City Hall South Korea');await page.locator('#nav-search').click();
  await page.waitForSelector('[data-nav-place="0"]',{timeout:40000});await page.locator('[data-nav-place="0"]').click();await page.waitForSelector('[data-nav="start"]',{timeout:40000});
  await page.waitForFunction(()=>[...document.querySelectorAll('#navigation-map .leaflet-tile-loaded')].some(i=>i.naturalWidth>0),null,{timeout:30000});
  assert.ok(responses.some(r=>r.url.includes('nominatim.openstreetmap.org')&&r.status===200));assert.ok(responses.some(r=>r.url.includes('router.project-osrm.org')&&r.status===200));
  await tilesReady();
  await page.evaluate(()=>{const p=document.createElement('p');p.id='live-proof-label';p.textContent='실제 OSM 지도·Nominatim 검색·OSRM 경로 연결 / GPS는 고정 검사용 좌표 · 실차 주행 아님';p.style.cssText='color:#ffcb7a;font-size:13px';document.querySelector('#navigation-panel h2').after(p)});
  await page.screenshot({path:path.join(out,'seoul-live-route-preview.png'),fullPage:true});
  if(await page.locator('#nav-access-confirm').count())await page.locator('#nav-access-confirm').check();
  await page.locator('[data-nav="start"]').click();await page.waitForFunction(()=>JSON.parse(CabinOS.navState()).active);
  assert.equal((await page.evaluate(()=>JSON.parse(CabinOS.navState()))).tracking,true);
  await tilesReady();
  await page.screenshot({path:path.join(out,'seoul-live-guidance.png'),fullPage:true});
  assert.deepEqual(errors,[]);result='passed';console.log('PASS live Chromium map tiles + destination search + driving route + shared guidance (fixed synthetic GPS, verified TLS)');
 }catch(e){failure=e.message;console.error(e);process.exitCode=1}
 finally{const report={type:'actual provider traffic in Chromium via session proxy with TLS verification; only GPS is a predefined synthetic public landmark',result,failure,responses,pageErrors:errors,physicalDeviceGps:'not tested',realRoadDriving:'not tested'};fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
