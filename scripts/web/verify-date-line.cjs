// Renderer boundary checks only: synthetic GPS/routes/places and blank local tile fixtures.
const {chromium}=require('playwright'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),out=path.join(root,process.env.CABIN_DATE_LINE_OUT||'artifacts/date-line-qa');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']}),page=await browser.newPage({viewport:{width:1440,height:1120}}),results=[],errors=[];
 let start=[-16.8,179.999],end=[-16.8,-179.999];page.on('pageerror',e=>errors.push(e.message));
 await page.route('https://tile.openstreetmap.org/**',r=>r.abort());
 await page.route('https://router.project-osrm.org/route/v1/driving/**',r=>r.fulfill({contentType:'application/json',body:JSON.stringify({code:'Ok',routes:[{distance:213,duration:30,geometry:{coordinates:[[start[1],start[0]],[end[1],end[0]]]},legs:[{steps:[{maneuver:{type:'depart',location:[start[1],start[0]]}},{maneuver:{type:'arrive',location:[end[1],end[0]]}}]}]}]})}));
 await page.route('https://overpass-api.de/api/interpreter**',r=>r.fulfill({contentType:'application/json',body:JSON.stringify({elements:[{type:'node',id:1,lat:end[0],lon:end[1],tags:{name:'Date line cafe fixture',amenity:'cafe'}}]})}));
 async function fresh(){
  await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');await page.waitForFunction(()=>cabinDemo?.state.parked);
  await page.evaluate(start=>{
   window.dateMaps={};const create=L.map;L.map=function(...a){return dateMaps[typeof a[0]==='string'?a[0]:a[0].id]=create.apply(this,a)};
   Object.defineProperty(navigator,'geolocation',{configurable:true,value:{
    watchPosition(success){window.dateLineGPS=success;success({coords:{latitude:start[0],longitude:start[1],accuracy:5,speed:0},timestamp:Date.now()});return 1},
    clearWatch(){},
    getCurrentPosition(success){success({coords:{latitude:start[0],longitude:start[1],accuracy:5},timestamp:Date.now()})}
   }});
  },start);
 }
 async function positions(id){return page.evaluate(id=>{const map=dateMaps[id],box=map.getSize();return [...Object.values(map._layers)].filter(x=>x instanceof L.CircleMarker).map(m=>{const p=map.latLngToContainerPoint(m.getLatLng());return {longitude:m.getLatLng().lng,x:p.x,y:p.y,visible:p.x>=-12&&p.y>=-12&&p.x<=box.x+12&&p.y<=box.y+12}})},id)}
 async function test(name,body){try{await body();results.push({name,result:'passed'});console.log('PASS',name)}catch(e){results.push({name,result:'failed',error:e.message});await page.screenshot({path:path.join(out,`failure-${results.length}.png`),fullPage:true});console.error('FAIL',name,e.message);process.exitCode=1}}
 try{
  for(const direction of ['east','west']){
   if(direction==='west'){start=[-16.8,-179.999];end=[-16.8,179.999]}
   await test(`navigation ${direction} keeps destination and GPS pins on the short date-line route`,async()=>{
    await fresh();await page.locator('aside [data-page="navigation"]').click();await page.locator('[data-nav="locate"]').click();await page.locator('#nav-query').fill(end.join(','));await page.locator('#nav-search').click();await page.locator('[data-nav-place="0"]').click();await page.waitForSelector('[data-nav="start"]');await page.waitForTimeout(500);
    const pins=await positions('navigation-map');assert.equal(pins.length,2);assert.ok(pins.every(p=>p.visible),JSON.stringify(pins));
    assert.ok(await page.evaluate(()=>Object.values(dateMaps['navigation-map']._layers).find(x=>x instanceof L.Polyline).getBounds().getEast()-Object.values(dateMaps['navigation-map']._layers).find(x=>x instanceof L.Polyline).getBounds().getWest()<.01));
   });
   await test(`navigation ${direction} preserves overlays when the map changes world copy`,async()=>{
    await page.evaluate(()=>{const m=dateMaps['navigation-map'];m.stop();m.panTo([m.getCenter().lat,m.getCenter().lng+(m.getCenter().lng>0?-360:360)],{animate:false});cabinDemo.render()});
    const pins=await positions('navigation-map');assert.ok(pins.every(p=>p.visible),JSON.stringify(pins));
    assert.ok(await page.evaluate(()=>{const m=dateMaps['navigation-map'],line=Object.values(m._layers).find(x=>x instanceof L.Polyline);return Math.abs(line.getBounds().getCenter().lng-m.getCenter().lng)<.01}));
   });
   await test(`navigation ${direction} follows moving GPS across the boundary without losing pins or tracking`,async()=>{
    await page.locator('[data-nav="start"]').click();
    const states=await page.evaluate(({start,end})=>{
     const now=Date.now.bind(Date);let offset=0;Date.now=()=>now()+offset;const records=[];
     const delta=(end[1]-start[1]+540)%360-180;
     for(let i=1;i<=46;i++){
      offset+=1000;const t=Math.min(1,i/44),lon=((start[1]+delta*t+540)%360)-180;
      dateLineGPS({coords:{latitude:start[0],longitude:lon,accuracy:5,speed:i>44?0:5,heading:delta>0?90:270},timestamp:Date.now()});cabinDemo.render();
      const n=JSON.parse(CabinOS.navState()),map=dateMaps['navigation-map'],box=map.getSize();
      records.push({tracking:n.tracking,offRoute:n.offRoute,arrived:n.arrived,pinCount:Object.values(map._layers).filter(x=>x instanceof L.CircleMarker).length,pinsVisible:Object.values(map._layers).filter(x=>x instanceof L.CircleMarker).every(pin=>{const p=map.latLngToContainerPoint(pin.getLatLng());return p.x>=-12&&p.y>=-12&&p.x<=box.x+12&&p.y<=box.y+12})});
     }
     return records;
    },{start,end});
    assert.ok(states.every(s=>s.tracking&&!s.offRoute&&s.pinCount===2&&s.pinsVisible),JSON.stringify(states));assert.equal(states.at(-1).arrived,true);
   });
   await test(`nearby ${direction} displays cafes across the date line and after world-copy panning`,async()=>{
    await fresh();await page.locator('aside [data-page="nearby"]').click();await page.locator('[data-nearby="locate"]').click();await page.locator('[data-nearby="search"]').click();await page.waitForFunction(()=>document.querySelector('#nearby-list').textContent.includes('Date line cafe fixture'));
    const pins=await positions('nearby-map');assert.equal(pins.length,2);assert.ok(pins.every(p=>p.visible),JSON.stringify(pins));
    await page.evaluate(()=>{const m=dateMaps['nearby-map'];m.stop();m.panTo([m.getCenter().lat,m.getCenter().lng+(m.getCenter().lng>0?-360:360)],{animate:false});cabinDemo.render()});assert.ok((await positions('nearby-map')).every(p=>p.visible));
   });
  }
  assert.deepEqual(errors,[]);
 }finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({scope:'Actual Chromium renderer with fixture roads/places/GPS and aborted tile requests; not a live Fiji route or map-data coverage claim.',results,pageErrors:errors},null,2));await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
