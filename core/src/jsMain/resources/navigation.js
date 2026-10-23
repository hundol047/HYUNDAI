'use strict';
(() => {
 const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const distance=m=>m>=1000?`${(m/1000).toFixed(1)} km`:`${Math.max(0,Math.round(m))} m`;
 let bridge,state,refresh,nav,map,line,marker,destination,revision=-1,lastFixPoint=null,watch=null,busy=false,notice='목적지를 검색하거나 위도,경도를 입력하세요.',results=[],selection=null,placePin=null,selectionKey='',nearbySearch=false,hasMore=false,lastSubmitted='',seenPlaceIds=[];
 let controller=null,generation=0,lastSearch=0,lastRoute=0,failures=0,spoken='',query='',lastLayout='',accessConfirmed=false,guidanceLayout='',resizeObserver=null,mapInteractive=null,lastAccent='',lastTracking=null,wasFollowing=false;
 let navigationVoice=false,voiceGeneration=0,roadTiles=null,roadHealth={status:"LOADING",loaded:0,visible:0};
 let searchAccessToken='',gpsNotice='GPS 미연결 · 내 위치 사용을 눌러 주세요.',locationGeneration=0,resumeLocation=false,gpsState='OFF',gpsStartedAt=null,lastGpsEventAt=null;
 let searchEndpoint='https://nominatim.openstreetmap.org',routeEndpoint='https://router.project-osrm.org';
 const cache=new Map(),$=id=>document.getElementById(id),etaFormat=new Intl.DateTimeFormat('ko-KR',{hour:'2-digit',minute:'2-digit'});
 const text=(id,value)=>{const e=$(id);if(e&&e.textContent!==value)e.textContent=value};
 const canPlan=()=>state?.parked&&!nav?.active&&!document.hidden;
 const valid=(lat,lon)=>Number.isFinite(lat)&&Number.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;
 function cancel(){generation++;controller?.abort();controller=null;busy=false}
 async function get(url,token=''){
  controller=new AbortController();const current=controller,signal=current.signal,timer=setTimeout(()=>{timedOut=true;current.abort()},25000);let timedOut=false;
  try {const r=await fetch(url,{signal,headers:{Accept:'application/json','Accept-Language':(nav?.language||'ko-KR')+',en;q=0.7',...(token?{Authorization:'Bearer '+token}:{})}});if(!r.ok)throw Error(r.status===429?'서비스 요청 한도를 넘었습니다. 잠시 후 다시 시도하세요.':url.includes('/route/v1/driving/')&&[400,404,422].includes(r.status)?`경로 요청을 처리하지 못했습니다 (${r.status}). 좌표·도로 연결·서버 주소를 확인하세요.`:`서버 연결 실패 (${r.status})`);
   const reader=r.body.getReader();const chunks=[];let size=0;
   try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>12000000)throw Error('경로가 너무 큽니다. 가까운 구간으로 나누세요.');chunks.push(value)}}finally{await reader.cancel().catch(()=>{})}
   const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength}return new TextDecoder().decode(bytes);
  }catch(e){if(timedOut)throw Error('서버 응답 시간이 지났습니다. 잠시 후 다시 검색하거나 목적지를 선택해 주세요.');throw e}finally{clearTimeout(timer);if(controller===current)controller=null}
 }
 async function request(body){cancel();const id=generation;busy=true;render();try{await body(id)}catch(e){if(id===generation&&e.name!=='AbortError')notice=e.message||'네트워크 연결을 확인하세요.'}finally{if(id===generation){busy=false;render();refresh?.()}}}
 function origin(){const f=nav?.fix;return f&&Date.now()-f.timestamp>=0&&Date.now()-f.timestamp<=10000&&f.accuracy<=50?f:null}
 function routeUrl(place){const f=origin();if(!f)throw Error('먼저 내 위치 사용으로 정확한 GPS를 받아 주세요.');return `${routeEndpoint}/route/v1/driving/${f.longitude},${f.latitude};${place.longitude},${place.latitude}?overview=full&geometries=geojson&steps=true&alternatives=false`}
 async function search(more=false){
  if(!canPlan()||busy||!state.network)return;query=$('nav-query').value.trim().slice(0,180);if(query.length<2)return;
  if(more&&(!hasMore||lastSubmitted!==query+'|'+nearbySearch))return;
  const f=origin();if(nearbySearch&&!f){notice='주변 가게 검색은 내 위치를 먼저 받아 주세요.';render();return}
  const previous=more?results:[];
  if(!more){seenPlaceIds=[];results=[];selection=null;hasMore=false;bridge.navClearPreview();nav=JSON.parse(bridge.navState())}
  const coords=query.split(',').map(s=>Number(s.trim()));if(!more&&coords.length===2&&coords.every(Number.isFinite)&&query.split(',').every(s=>s.trim()!=='')){results=valid(...coords)?[{name:`좌표 목적지 ${coords[0].toFixed(6)}, ${coords[1].toFixed(6)}`,latitude:coords[0],longitude:coords[1],source:'좌표 입력'}]:[];notice='좌표는 위도,경도 순서입니다.';render();return}
  lastSubmitted=query+'|'+nearbySearch;
  await request(async id=>{
   const queries=JSON.parse(bridge.placeRequests(query,f?.latitude??NaN,f?.longitude??NaN,nearbySearch,JSON.stringify(more?seenPlaceIds:[])));const rows=[];
   for(const params of queries){
    const key=searchEndpoint+'|'+nav.language+'|'+JSON.stringify(params);let data;
    if(cache.has(key))data=cache.get(key);else {
     await new Promise(r=>setTimeout(r,Math.max(0,1500-(Date.now()-lastSearch))));if(id!==generation||!canPlan())return;lastSearch=Date.now();
     const raw=await get(`${searchEndpoint}/search?${new URLSearchParams(params)}`,searchAccessToken);if(id!==generation||!canPlan())return;const parsed=JSON.parse(raw);if(!Array.isArray(parsed))throw Error('검색 응답 형식을 확인하세요.');data=raw;cache.set(key,data);if(cache.size>30)cache.delete(cache.keys().next().value);
    }
    rows.push(...JSON.parse(bridge.placeResults(data,f?.latitude??NaN,f?.longitude??NaN,false)));
   }
   if(id!==generation||!canPlan())return;
   const unique=[...new Map(rows.map(p=>[p.id||[p.name,p.latitude,p.longitude].join('|'),p])).values()].filter(p=>!nearbySearch||L.latLng(f.latitude,f.longitude).distanceTo([p.latitude,p.longitude])<=20000).sort((a,b)=>nearbySearch?L.latLng(f.latitude,f.longitude).distanceTo([a.latitude,a.longitude])-L.latLng(f.latitude,f.longitude).distanceTo([b.latitude,b.longitude]):0).slice(0,6);
   results=[...new Map([...previous,...unique].map(p=>[p.id||[p.name,p.latitude,p.longitude].join('|'),p])).values()].slice(0,18);
   const oldSeen=seenPlaceIds.length;seenPlaceIds=[...new Set([...seenPlaceIds,...rows.map(p=>p.id).filter(Boolean)])].slice(0,18);hasMore=rows.length>=6&&seenPlaceIds.length>oldSeen&&seenPlaceIds.length<18&&results.length<18;
   notice=!unique.length&&hasMore?'이번 결과에 20km 내 가게가 없습니다. 다른 결과 더 보기를 눌러 확인하세요.':unique.length?'가게명·주소·출처를 확인하고 선택하세요. 검색 결과는 전체 가게 목록이 아닙니다.':'등록된 검색 결과가 없습니다. 가게명·지점·도시나 현지어를 바꿔 보세요. 누락된 가게가 있을 수 있습니다.';
  })
 }
 async function plan(place){
  if(!canPlan()||busy)return;selection=place;accessConfirmed=false;bridge.navClearPreview();nav=JSON.parse(bridge.navState());render();
  if(!state.network){notice='선택한 가게 위치는 표시합니다. 새 자동차 경로는 온라인 연결이 필요합니다.';render();return}
  if(!origin()){notice='선택한 가게를 지도에 표시했습니다. 자동차 경로는 내 위치의 정확한 GPS를 먼저 받아 주세요.';render();return}
  const f=origin();if(L.latLng(f.latitude,f.longitude).distanceTo([place.latitude,place.longitude])<20){notice='목적지가 현재 위치와 매우 가깝습니다. 지도에서 위치를 확인하세요.';render();return}
  await request(async id=>{
   await new Promise(r=>setTimeout(r,Math.max(0,1500-(Date.now()-lastRoute))));if(id!==generation||!canPlan())return;
   const url=routeUrl(place);lastRoute=Date.now();const raw=await get(url);if(id!==generation||!canPlan())return;
   const error=bridge.navPreview(raw,place.name,place.latitude,place.longitude);if(error)throw Error(error);destination=place;nav=JSON.parse(bridge.navState());notice='경로를 확인하고 안내 시작을 누르세요. 실시간 교통·차선 정보는 제공하지 않습니다.';
  })
 }

 function setGps(status,message){gpsState=status;gpsNotice=message}
 async function locate(){
  if((!canPlan()&&!nav?.active&&!nav?.safetyHeld)||document.hidden)return;
  stopWatch();bridge.navBackground();nav=JSON.parse(bridge.navState());
  if(!navigator.geolocation){setGps('DISABLED','이 브라우저는 위치 서비스를 지원하지 않습니다. HTTPS와 기기 위치 설정을 확인하세요.');render();return}
  if(!canPlan()){
   const pending=locationGeneration;let permission='unknown';
   try{permission=(await navigator.permissions?.query({name:'geolocation'}))?.state||'unknown'}catch{}
   if(pending!==locationGeneration||document.hidden)return;
   if(!canPlan()&&permission!=='granted'){setGps('DENIED','주행 중에는 새 위치 권한 창을 열지 않습니다. 안전하게 주차한 뒤 위치 권한을 허용해 주세요.');render();return}
  }
  setGps('WAITING','GPS 수신 중 · 정확한 위치 권한이 필요합니다.');gpsStartedAt=Date.now();
  const epoch=++locationGeneration;watch='pending';
  try {
   const id=navigator.geolocation.watchPosition(p=>{
    if(epoch!==locationGeneration||document.hidden)return;
    const c=p.coords;bridge.navFix(c.latitude,c.longitude,c.accuracy,p.timestamp,c.speed??-1,c.heading??-1);nav=JSON.parse(bridge.navState());lastGpsEventAt=Date.now();
    if(!nav.gpsReady||nav.active&&!nav.tracking)setGps('POOR','위치가 부정확하거나 오래됐습니다. 정확한 GPS를 기다려 주세요.');
    else if(nav.fix?.timestamp===p.timestamp)setGps('READY','기기 위치 확인 · 정확도 약 '+Math.round(c.accuracy)+'m');
    render();refresh?.();speak();reroute()
   },e=>{
    if(epoch!==locationGeneration)return;
    bridge.navBackground();nav=JSON.parse(bridge.navState());stopNavigationVoice();
    if(e.code===1){stopWatch();setGps('DENIED','정확한 위치 권한이 필요합니다. 브라우저의 사이트 위치 권한을 허용해 주세요.')}
    else {lastGpsEventAt=Date.now();setGps('STALE',e.code===3?'GPS 수신 시간이 지났습니다. 새 위치를 기다리거나 다시 연결해 주세요.':'위치를 받지 못했습니다. 위치 설정을 확인하세요. 새 위치가 오면 자동 복구합니다.')}
    render();refresh?.()
   },{enableHighAccuracy:true,maximumAge:0,timeout:15000});
   if(epoch===locationGeneration)watch=id;else navigator.geolocation.clearWatch(id);
  }catch(e){stopWatch();setGps('DENIED','위치 서비스를 시작하지 못했습니다. HTTPS·사이트 위치 권한·기기 위치 설정을 확인해 주세요.');bridge.navBackground();nav=JSON.parse(bridge.navState())}
  render()
 }
 function resumeGPS(){if(resumeLocation&&!document.hidden&&(canPlan()||nav?.active||nav?.safetyHeld)){resumeLocation=false;locate()}}
 function stopWatch(){locationGeneration++;if(typeof watch==='number')navigator.geolocation?.clearWatch(watch);watch=null;gpsStartedAt=null;lastGpsEventAt=null}
 function refreshGpsStatus(){
  if(watch!==null&&!nav?.gpsReady&&(gpsState==='READY'||gpsState==='WAITING'&&gpsStartedAt!==null&&Date.now()-gpsStartedAt>=15000||lastGpsEventAt!==null&&Date.now()-lastGpsEventAt>10000))setGps('STALE','GPS 응답이 오래됐습니다. 새 위치를 기다리거나 다시 연결해 주세요.');
 }
 function stopNavigationVoice(){voiceGeneration++;if(navigationVoice)window.speechSynthesis?.cancel();navigationVoice=false;spoken=''}
 function voiceFor(tag){const voices=window.speechSynthesis?.getVoices()||[],base=tag.split('-')[0].toLowerCase();return voices.find(v=>v.lang.toLowerCase()===tag.toLowerCase())||voices.find(v=>v.lang.split('-')[0].toLowerCase()===base)}
 function announce(text,tag){const voice=voiceFor(tag);if(!voice||!window.SpeechSynthesisUtterance){window.EchoVoice?.notice('선택한 언어의 음성팩이 없습니다. 화면 안내를 사용하거나 기기 음성팩을 설치하세요.');return false}const utterance=new SpeechSynthesisUtterance(text);utterance.lang=voice.lang;utterance.voice=voice;const speechId=++voiceGeneration;utterance.onend=utterance.onerror=()=>{if(speechId===voiceGeneration)navigationVoice=false};speechSynthesis.cancel();navigationVoice=true;speechSynthesis.speak(utterance);return true}
 function speak(){if(!window.speechSynthesis||window.EchoVoice?.listening)return;if(!nav?.tracking||nav.offRoute||!state.voice){stopNavigationVoice();return;}const band=nav.arrived?'arrived':nav.turnMeters<60?'now':nav.turnMeters<300?'soon':'ahead';const key=nav.instruction+'|'+band+'|'+nav.instructionLanguage;if(key===spoken)return;const distance=new Intl.NumberFormat(nav.instructionLanguage,{style:'unit',unit:'meter',unitDisplay:'long',maximumFractionDigits:0}).format(nav.turnMeters);if(announce(nav.arrived?nav.instruction:bridge.navSpoken(distance,nav.instruction),nav.instructionLanguage))spoken=key}
 function setLanguage(tag){if(!canPlan()||!bridge.navSetLanguage(tag))return false;cancel();stopNavigationVoice();cache.clear();results=[];hasMore=false;nav=JSON.parse(bridge.navState());render();return true}
 async function voiceCommand(raw){
  if(document.hidden)return;const c=JSON.parse(bridge.navVoiceCommand(raw));
  if(c.action==='STOP'){document.querySelector('[data-nav="stop"]')?.click();return}
  if(c.action==='REPEAT'){spoken='';speak();return}
  if(c.action==='CANCEL'){cancel();window.EchoVoice?.cancel('요청을 취소했습니다.');render();return}
  if(!canPlan()){window.EchoVoice?.notice('새 목적지·결과 선택·안내 시작은 안전한 주차 확인 후 가능합니다. 주행 중에는 안내 반복·종료를 말하세요.');return}
  if(c.action==='SEARCH'){if(!c.query){window.EchoVoice?.notice('목적지를 조금 더 자세히 말씀해 주세요.');return}cancel();query=c.query;$('nav-query').value=query;const voiceLanguage=nav.language;await search();if(canPlan()&&voiceLanguage===nav.language&&query===c.query&&results.length&&state.voice)announce(results.slice(0,6).map((p,i)=>`${i+1}. ${p.name}`).join('. '),nav.language)}
  if(c.action==='SELECT'){const p=results[c.index];if(!p){window.EchoVoice?.notice('검색 결과에 있는 번호를 말씀해 주세요.');return}if(busy){window.EchoVoice?.notice('경로 계산이 끝난 뒤 다시 말씀해 주세요.');return}await plan(p);if(canPlan()&&selection===p&&nav.hasRoute&&state.voice)announce(p.name+', '+distance(nav.remainingMeters),nav.language)}
  if(c.action==='START'){if(busy||!nav.hasRoute){window.EchoVoice?.notice('먼저 검색 결과 번호를 말하고 경로 계산을 기다려 주세요.');return}if(nav.destinationAccessMeters>100&&!accessConfirmed){window.EchoVoice?.notice('목적지와 차량 접근 지점 차이를 주차 중 화면에서 먼저 확인해 주세요.');return}document.querySelector('[data-nav="start"]')?.click()}
 }

 async function reroute(){if(!nav?.active||!destination||busy||failures>=3||!state.network||document.hidden||!bridge.navReroute())return;await request(async id=>{try{lastRoute=Date.now();const raw=await get(routeUrl(destination));if(id!==generation||!nav.active)return;if(!bridge.navReplace(raw))throw Error('재탐색 경로를 적용하지 못했습니다. GPS를 확인하세요.');failures=0;spoken='';notice='경로를 다시 계산했습니다.';nav=JSON.parse(bridge.navState());speak()}catch(e){if(e.name!=='AbortError')failures++;throw e}})}
 // Draw maneuvers independently of the device's font coverage. Some browsers
 // render the Unicode turn arrows as a missing-glyph box while driving.
 const maneuverPaths={
  '↑':'M24 42V7M12 19 24 7l12 12',
  '↰':'M35 42V23a9 9 0 0 0-9-9H8m12-10L8 14l12 10',
  '↱':'M13 42V23a9 9 0 0 1 9-9h18M28 4l12 10-12 10',
  '↶':'M36 42V18a12 12 0 0 0-24 0v13m-8-9 8 9 8-9',
  '⟳':'M33 10a16 16 0 1 0 6 22M33 10h10V0',
 };
 function renderManeuver(symbol){
  const e=$('nav-symbol');if(!e||e.dataset.symbol===symbol)return;
  e.dataset.symbol=symbol;e.setAttribute('aria-hidden','true');
  const shape=symbol==='●'?'<circle cx="24" cy="24" r="14"/>':`<path d="${maneuverPaths[symbol]||maneuverPaths['↑']}"/>`;
  e.innerHTML=`<svg viewBox="0 0 48 48" width="48" height="48" fill="none" stroke="currentColor" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round" focusable="false">${shape}</svg>`;
 }
 function renderGuidance(planAllowed){
  const confirm=planAllowed&&nav.destinationAccessMeters>100;
  const layout=[nav.hasRoute,planAllowed,confirm,nav.active&&!nav.tracking,nav.offRoute,Boolean(selection),gpsState].join('|');
  if(guidanceLayout!==layout){
   guidanceLayout=layout;
   $('nav-guidance').innerHTML=nav.hasRoute?`<h3 id="nav-destination"></h3><p id="nav-destination-address"></p><strong class="nav-instruction" id="nav-instruction"></strong><b class="nav-turn" id="nav-turn"><span class="nav-symbol" id="nav-symbol"></span><span id="nav-turn-distance"></span></b><p>남은 거리 <b id="nav-remaining"></b></p><p id="nav-eta"></p><p class="nav-access" id="nav-access"></p><p class="nav-access" id="nav-origin-access"></p>${confirm?`<label><input id="nav-access-confirm" type="checkbox" ${accessConfirmed?'checked':''}>목적지와 차량 접근 지점의 차이를 확인했습니다.</label>`:''}<small>실시간 교통·차선 안내 미제공</small>${planAllowed?'<button data-nav="start">안내 시작</button><button data-nav="replan">현재 위치로 경로 다시 계산</button>':''}<button data-nav="stop">안내 종료·경로 지우기</button>${nav.active&&!nav.tracking&&!['POOR','STALE','DENIED','DISABLED'].includes(gpsState)?'<button data-nav="gps">GPS 다시 연결</button>':''}${nav.offRoute?'<button data-nav="retry">재탐색 재시도</button>':''}`:'<p id="nav-empty"></p>'+ (selection&&planAllowed?'<button data-nav="clear">선택 지우기</button>':'');
  }
  if(!nav.hasRoute){text('nav-empty',planAllowed?'국가 제한 없이 검색합니다. 정확한 기기 GPS로 출발 위치를 정해 주세요.':'목적지 설정은 안전한 주차 확인 후 가능합니다.');return}
  text('nav-destination',nav.destination);text('nav-destination-address',selection?.address||'');$('nav-destination-address').hidden=!selection?.address;text('nav-instruction',nav.active?(nav.tracking&&!nav.offRoute?nav.instruction:'위치·경로 확인 중'):nav.arrived?nav.instruction:'자동차 경로 미리보기');
  $('nav-turn').hidden=!(nav.active&&nav.tracking&&!nav.offRoute);renderManeuver(nav.turnSymbol||'↑');text('nav-turn-distance',distance(nav.turnMeters)+' 앞');
  text('nav-remaining',distance(nav.remainingMeters));text('nav-eta',nav.arrived?'차량 경로 도착 · 안내 종료':nav.active&&(!nav.tracking||nav.offRoute)?'도착 예상 갱신 중지':`약 ${Math.max(1,Math.ceil(nav.remainingSeconds/60))}분 · ${etaFormat.format(new Date(Date.now()+nav.remainingSeconds*1000))} 도착 예상`);
  $('nav-access').hidden=!(nav.destinationAccessMeters>30);text('nav-access',`차량 접근 지점에서 목적지까지 직선 약 ${distance(nav.destinationAccessMeters)} · 주차장·진입로를 확인하세요.`);
  text('nav-origin-access',nav.originAccessMeters>45?`출발 GPS와 연결도로 시작점이 약 ${distance(nav.originAccessMeters)} 떨어져 있습니다. 도로 근처에서 정확한 위치를 확인하세요. 이 사이 진입도로는 안내하지 않습니다.`:'출발 위치의 정확하고 최신인 GPS를 확인해 주세요.');$('nav-origin-access').hidden=!planAllowed||nav.originReady;
  const start=$('nav-guidance').querySelector('[data-nav="start"]');if(start)start.disabled=busy||!nav.originReady||(confirm&&!accessConfirmed);
  const replan=$('nav-guidance').querySelector('[data-nav="replan"]');if(replan)replan.disabled=busy||!state.network||!(selection||destination);
  const retry=$('nav-guidance').querySelector('[data-nav="retry"]');if(retry)retry.disabled=busy||!state.network;
 }
 function render(){if(!$('navigation-panel'))return;window.EchoVoice?.update(window.EchoNavigation,bridge,state);const planAllowed=canPlan(),layout=String(planAllowed);
  if(lastLayout!==layout){lastLayout=layout;$('nav-planning').hidden=!planAllowed;$('nav-results').hidden=!planAllowed}
  const external=$('nav-external-search');if(external){external.href='https://www.google.com/maps/search/?api=1&query='+encodeURIComponent(query);external.hidden=!query.trim()}
  $('nav-search').disabled=busy||!state.network;$('nav-locate').disabled=busy;$('nav-cancel').hidden=!busy||!planAllowed;refreshGpsStatus();text('nav-gps-status',gpsNotice+(nav.gpsReady&&nav.gpsAgeSeconds!==null?' · '+nav.gpsAgeSeconds+'초 전 수신':''));$('nav-gps-help').hidden=!['POOR','STALE','DENIED','DISABLED'].includes(gpsState);$('nav-status').textContent=nav.active||nav.arrived?nav.status:notice;
  $('nav-notice').textContent=(busy?'요청 중… ':'')+notice+(!state.network?' · 오프라인: 새 지도·검색·재탐색 불가':'');
  if(document.activeElement!==$('nav-query'))$('nav-query').value=query;
  const resultKey=JSON.stringify(results)+String(busy)+String(hasMore);if($('nav-results').dataset.key!==resultKey){$('nav-results').dataset.key=resultKey;$('nav-results').innerHTML=results.map((p,i)=>`<button data-nav-place="${i}" ${busy?'disabled':''}><b>${i+1}. ${esc(p.name)}</b>${p.address?`<small>${esc(p.address)}</small>`:''}<small>${esc([p.category,p.source].filter(Boolean).join(' · '))}</small>${p.phone?`<small>전화 ${esc(p.phone)}</small>`:''}${p.openingHours?`<small>등록 영업시간 ${esc(p.openingHours)} · 현재 영업 여부 미확인</small>`:''}<small>지도에 표시 · 자동차 경로 계산 →</small></button>${p.website?`<a href="${esc(p.website)}" data-place-external target="_blank" rel="noopener noreferrer">상세정보/웹사이트 ↗</a>`:''}`).join('')+(hasMore?`<button data-nav="more" ${busy?'disabled':''}>다른 결과 더 보기 (최대 18개)</button>`:'')}

  renderGuidance(planAllowed);
  roadTiles?.setOnline(state.network);renderRoadHealth();
  if(!map&&window.L){map=L.map('navigation-map',{trackResize:false,worldCopyJump:true,minZoom:1,maxZoom:19}).setView([20,0],2);const roadLayer=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,updateWhenIdle:true,updateWhenZooming:false,keepBuffer:2,attribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'}).addTo(map);roadTiles=window.CabinRoadTiles.attach(map,roadLayer,h=>{roadHealth=h;renderRoadHealth()});roadTiles.setOnline(state.network);resizeObserver=new ResizeObserver(()=>{if(map){map.stop();map.invalidateSize({animate:false,debounceMoveend:true})}});resizeObserver.observe($('navigation-map'))}
  if(!map)return;const accent=getComputedStyle(document.documentElement).getPropertyValue('--blue').trim();if(line&&accent!==lastAccent)line.setStyle({color:accent});lastAccent=accent;
  if(mapInteractive!==planAllowed){mapInteractive=planAllowed;if(planAllowed&&!map.zoomControl._map)map.zoomControl.addTo(map);if(!planAllowed&&map.zoomControl._map)map.zoomControl.remove();for(const handler of ['dragging','scrollWheelZoom','doubleClickZoom','boxZoom','keyboard','touchZoom'])map[handler]?.[planAllowed?'enable':'disable']()}
  if(revision!==nav.revision){revision=nav.revision;if(line){map.removeLayer(line);line=null}const geometry=bridge.navGeometryPoints?bridge.navGeometryPoints():JSON.parse(bridge.navGeometry());if(geometry){const wrapped=[];for(const p of geometry){let lon=p[1];if(wrapped.length){while(lon-wrapped.at(-1)[1]>180)lon-=360;while(lon-wrapped.at(-1)[1]<-180)lon+=360}wrapped.push([Math.max(-85,Math.min(85,p[0])),lon])}line=L.polyline(wrapped,{color:accent||'#58a9ff',weight:6}).addTo(map);const bounds=line.getBounds();if(nav.fix&&!nav.active){let lon=nav.fix.longitude;const reference=wrapped[0][1];while(lon-reference>180)lon-=360;while(lon-reference< -180)lon+=360;bounds.extend([nav.fix.latitude,lon])}map.fitBounds(bounds,{padding:[30,30],maxZoom:16})}}
  const nextSelection=selection?[selection.id,selection.latitude,selection.longitude,selection.name].join('|'):'';
  if(nextSelection!==selectionKey){selectionKey=nextSelection;if(placePin){map.removeLayer(placePin);placePin=null}if(selection){const point=[Math.max(-85,Math.min(85,selection.latitude)),selection.longitude];placePin=L.circleMarker(point,{radius:9,color:'#ffcb7a',fillColor:'#a46d26',fillOpacity:1,weight:3}).bindTooltip(document.createTextNode(selection.name+(selection.address?' · '+selection.address:''))).addTo(map);if(!nav.hasRoute)map.setView(point,16,{animate:false})}}
  const following=nav.active&&nav.tracking;
  if(nav.fix){let lon=nav.fix.longitude;const reference=map.getCenter().lng;while(lon-reference>180)lon-=360;while(lon-reference< -180)lon+=360;const point=[Math.max(-85,Math.min(85,nav.fix.latitude)),lon];const moved=!lastFixPoint||point[0]!==lastFixPoint[0]||point[1]!==lastFixPoint[1];
   if(!marker)marker=L.circleMarker(point,{radius:9,color:'#fff',fillOpacity:1,fillColor:'#62e2c3'}).addTo(map);else if(moved)marker.setLatLng(point);
   if(lastTracking!==nav.tracking){marker.setStyle({fillColor:nav.tracking?'#62e2c3':'#81949c'});lastTracking=nav.tracking}
   if((following&&(moved||!wasFollowing))||(!lastFixPoint&&!nav.hasRoute&&!selection))map.setView(point,Math.max(map.getZoom(),15),{animate:false});lastFixPoint=point;
  }else if(marker){map.removeLayer(marker);marker=null;lastFixPoint=null;lastTracking=null}
  wasFollowing=following;

 }
 function renderRoadHealth(){const e=$('nav-map-status');if(!e)return;text('nav-map-status',window.CabinRoadTiles.message(roadHealth));$('nav-road-health').hidden=roadHealth.status==='READY';$('nav-tiles-retry').hidden=roadHealth.status!=='UNAVAILABLE'||!canPlan()}
 document.addEventListener('click',e=>{if(e.target.closest('#nav-external-search,[data-place-external]')&&!canPlan())e.preventDefault()},true);
 const html=()=>`<section id="navigation-panel"><h2>EchoRoad · 글로벌 내비게이션</h2>${window.EchoVoice?.html()||''}<p id="nav-status" role="status"></p><p id="nav-gps-status" class="mini-note"></p><div id="nav-gps-help" hidden><p class="mini-note">브라우저의 사이트 위치 권한과 기기 위치 서비스를 확인하세요. HTTPS에서 사용하고, 가능하면 정확한 위치를 허용해 주세요. 실내에서는 수신이 늦거나 부정확할 수 있습니다.</p><button type="button" data-nav="gps">GPS 다시 연결</button></div><div id="nav-planning"><form id="nav-search-form"><input id="nav-query" aria-label="전 세계 목적지" maxlength="180" placeholder="가게명·지점·도시·주소 또는 위도,경도"><button id="nav-search" type="submit">검색</button><button id="nav-locate" type="button" data-nav="locate">내 위치 사용</button><button id="nav-cancel" type="button" data-nav="cancel" hidden>요청 취소</button></form><label class="nav-nearby-option"><input id="nav-nearby-only" type="checkbox" ${nearbySearch?'checked':''}>내 위치 주변 20km에서 찾기 · 결과에 없는 가게가 있을 수 있습니다.</label><a id="nav-external-search" target="_blank" rel="noopener noreferrer">Google Maps에서 추가 검색 ↗</a><details><summary>서비스 설정</summary><p>기본 공개 서버는 체험용입니다. 운영 시 자체·계약 서버로 교체하세요. 입력한 서버에 검색어·좌표가 전송됩니다.</p><form id="nav-service-form"><label>주소·가게 검색 HTTPS <input name="search" value="${esc(searchEndpoint)}" required></label><label>OSRM HTTPS <input name="route" value="${esc(routeEndpoint)}" required></label><label>자체 서버 접근키 · 저장하지 않음 <input name="token" type="password" autocomplete="off" maxlength="256"></label><p>상호 데이터 API 키는 서버에 보관합니다. 접근키는 이 탭에서만 사용합니다.</p><button>적용</button></form></details></div><div class="navigation-grid"><div class="nav-map-wrap"><div id="navigation-map" aria-label="자동차 경로 지도"></div><div id="nav-road-health"><span id="nav-map-status" role="status"></span><button id="nav-tiles-retry" type="button" data-nav="tiles" hidden>도로 지도 다시 불러오기</button></div></div><div class="navigation-details"><div id="nav-results"></div><section id="nav-guidance" class="panel"></section><p id="nav-notice" role="status"></p></div></div><p class="mini-note">© OpenStreetMap contributors · Nominatim 검색 · OSRM 자동차 경로 · 국가별 도로 데이터와 서버 가용성에 따라 경로가 없을 수 있습니다. 차량·위험 정보는 별도 데모 데이터입니다.</p></section>`;
 document.addEventListener('submit',event=>{if(event.target.id==='nav-search-form'){event.preventDefault();search()}if(event.target.id==='nav-service-form'){event.preventDefault();if(!canPlan())return;try{const data=new FormData(event.target);const urls=['search','route'].map(k=>new URL(String(data.get(k))));if(urls.some(u=>u.protocol!=='https:'||u.username||u.password||u.search||u.hash))throw Error('HTTPS 기본 주소만 입력하세요.');const token=String(data.get('token')||'');if(token&&(token.length<12||token.length>256||!/^[!-~]+$/.test(token)||urls[0].origin==='https://nominatim.openstreetmap.org'))throw Error('접근키는 자체 검색 서버에만 사용하며 12~256자의 공백 없는 값이어야 합니다.');cancel();searchAccessToken=token;event.target.elements.token.value='';[searchEndpoint,routeEndpoint]=urls.map(u=>u.href.replace(/\/$/,''));cache.clear();results=[];selection=null;hasMore=false;bridge.navClearPreview();nav=JSON.parse(bridge.navState());notice='서비스 주소를 변경했습니다. 가게를 다시 검색하세요.'}catch(e){notice=e.message}render()}});
 document.addEventListener('change',event=>{if(event.target.id==='nav-nearby-only'&&canPlan()){if(busy){cancel();notice='검색 범위를 바꿔 이전 요청을 취소했습니다. 다시 검색해 주세요.'}nearbySearch=event.target.checked;hasMore=false;render()}if(event.target.id==='nav-access-confirm'&&canPlan()){accessConfirmed=event.target.checked;render()}});
 document.addEventListener('input',event=>{if(event.target.id==='nav-query'){if(query!==event.target.value&&busy&&canPlan()){cancel();notice='입력 변경으로 이전 요청을 취소했습니다. 검색을 눌러 주세요.'}query=event.target.value;if(lastSubmitted!==query+'|'+nearbySearch){hasMore=false;render()}const link=$('nav-external-search');if(link){link.href='https://www.google.com/maps/search/?api=1&query='+encodeURIComponent(query);link.hidden=!query.trim()}}});
 document.addEventListener('click',event=>{const b=event.target.closest('button');if(!b||b.disabled||!bridge)return;if(b.dataset.navPlace!==undefined){plan(results[+b.dataset.navPlace]);return}if(!b.dataset.nav)return;switch(b.dataset.nav){case'tiles':if(canPlan())roadTiles?.retry();break;case'cancel':if(canPlan()){cancel();notice='요청을 취소했습니다. 검색어나 목적지를 바꿀 수 있습니다.'}break;case'clear':if(canPlan()){cancel();selection=null;destination=null;bridge.navClearPreview();notice='선택한 장소를 지웠습니다.'}break;case'replan':if(canPlan()&&(selection||destination)){plan(selection||destination);return}break;case'more':search(true);return;case'locate':case'gps':locate();break;case'start':if(canPlan()){if(bridge.navStart(accessConfirmed)){spoken='';failures=0;notice='GPS 주행 안내를 시작했습니다.'}else notice='출발 위치의 정확한 GPS가 필요합니다. 이동했다면 경로를 다시 계산하세요.'}break;case'stop':cancel();bridge.navStop();destination=null;selection=null;results=[];hasMore=false;spoken='';if(!JSON.parse(bridge.navState()).safetyHeld){resumeLocation=false;stopWatch();setGps('OFF','GPS 미연결 · 내 위치 사용을 눌러 주세요.')}notice='안내 종료 · 이동이 감지됐다면 정차 상태를 다시 확인합니다.';break;case'retry':failures=0;reroute();break}nav=JSON.parse(bridge.navState());render();refresh?.()});
 document.addEventListener('visibilitychange',()=>{if(!bridge)return;if(document.hidden){resumeLocation=resumeLocation||watch!==null;cancel();stopWatch();window.speechSynthesis?.cancel();bridge.navBackground();nav=JSON.parse(bridge.navState());setGps('OFF','화면 밖에서는 위치 수신 중지')}else{if(nav?.active||nav?.safetyHeld)resumeLocation=true;resumeGPS()}});
 window.EchoNavigation={html,voiceCommand,setLanguage,pauseVoice:stopNavigationVoice,get canPlan(){return canPlan()},get language(){return nav?.language||'ko-KR'},get instructionLanguage(){return nav?.instructionLanguage||'ko'},update(s,b,r){bridge=b;state=s;refresh=r;nav=JSON.parse(b.navState());if(!s.network&&busy)cancel();resumeGPS();render();speak();reroute()},destroy(){roadTiles?.destroy();roadTiles=null;roadHealth={status:"LOADING",loaded:0,visible:0};window.EchoVoice?.cancel();resizeObserver?.disconnect();resizeObserver=null;map?.remove();map=null;line=null;marker=null;placePin=null;selectionKey='';revision=-1;lastFixPoint=null;lastLayout='';guidanceLayout='';mapInteractive=null;lastAccent='';lastTracking=null;wasFollowing=false},reset(){window.EchoVoice?.reset();cancel();stopWatch();cache.clear();searchAccessToken='';results=[];seenPlaceIds=[];selection=null;hasMore=false;nearbySearch=false;query='';destination=null;resumeLocation=false;setGps('OFF','GPS 미연결 · 내 위치 사용을 눌러 주세요.');notice='개인 검색·위치 정보를 초기화했습니다.'},get active(){return nav?.active===true},get guidance(){return nav}};
})();
