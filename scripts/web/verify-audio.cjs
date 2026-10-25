const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CABIN_CHROME||'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 const page=await browser.newPage({viewport:{width:390,height:844}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
   window.audioEvidence=[];const Original=window.AudioContext||window.webkitAudioContext;
   window.AudioContext=class extends Original {
     createBufferSource(){const node=super.createBufferSource(),start=node.start.bind(node);node.start=(...args)=>{const samples=node.buffer.getChannelData(0);window.audioEvidence.push({state:this.state,rms:Math.sqrt(samples.reduce((sum,x)=>sum+x*x,0)/samples.length),peak:Math.max(...samples.slice(0,40000).map(Math.abs)),loop:node.loop});return start(...args)};return node;}
   };
 });
 try {
   await page.goto(process.env.CABIN_DEMO_URL||'http://127.0.0.1:4173/');
   await page.waitForFunction(()=>window.cabinDemo?.state.parked);
   await page.locator('[data-start="FOCUS"]').click();
   for(const name of ['백색 소음','자연의 소리','잔잔한 톤']){
     await page.locator(`[data-sound="${name}"]`).click();
     await page.waitForFunction(n=>document.querySelector(`[data-sound="${n}"]`).textContent.includes('중지'),name);
   }
   const evidence=await page.evaluate(()=>audioEvidence);assert.equal(evidence.length,3);
   for(const x of evidence){assert.equal(x.state,'running');assert.equal(x.loop,true);assert.ok(x.rms*.14>.12&&x.rms*.14<.16);assert.ok(x.peak*.14<.66);}
   await page.locator('[data-sound="잔잔한 톤"]').click();assert.doesNotMatch(await page.locator('[data-sound="잔잔한 톤"]').textContent(),/중지/);
   assert.deepEqual(errors,[]);console.log('PASS 3 real WebAudio sources running with bounded nonzero PCM; mobile toggle stops playback. Chromium, not physical iPhone audio.');
   fs.mkdirSync('artifacts/web-audio',{recursive:true});fs.writeFileSync('artifacts/web-audio/results.json',JSON.stringify({browser:'Chromium mobile viewport; not physical iPhone',presets:evidence,pageErrors:errors},null,2));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
