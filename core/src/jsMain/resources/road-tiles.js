'use strict';
// A route line / GPS fix is not proof that the visible basemap actually loaded.
window.CabinRoadTiles={attach(map,layer,notify){
 let closed=false,online=true,since=Date.now(),frame=null,last='',current={status:'LOADING',loaded:0,visible:0};
 function sample(){
  if(closed)return current;
  const host=map.getContainer(),r=host.getBoundingClientRect();
  const visible=[...layer.getContainer()?.querySelectorAll('img.leaflet-tile')||[]].filter(i=>{const b=i.getBoundingClientRect();return b.width>0&&b.height>0&&b.right>r.left&&b.left<r.right&&b.bottom>r.top&&b.top<r.bottom});
  const loaded=visible.filter(i=>i.complete&&i.naturalWidth>0).length;
  const status=!online?'OFFLINE':visible.length>0&&loaded===visible.length?'READY':Date.now()-since>=15000?'UNAVAILABLE':loaded>0?'PARTIAL':'LOADING';
  current={status,loaded,visible:visible.length};const key=JSON.stringify(current);if(key!==last){last=key;notify(current)}return current;
 }
 function schedule(){if(closed||frame!==null)return;frame=requestAnimationFrame(()=>{frame=null;sample()})}
 function moved(){since=Date.now();schedule()}
 for(const event of ['tileloadstart','tileload','tileerror','load'])layer.on(event,schedule);
 map.on('movestart',moved);map.on('moveend zoomend resize',schedule);
 const timer=setInterval(sample,1000);schedule();
 return {sample,setOnline(value){if(online===value)return;online=value;since=Date.now();sample();if(value)layer.redraw()},retry(){if(!online||closed)return;since=Date.now();layer.redraw();sample()},destroy(){closed=true;clearInterval(timer);if(frame!==null)cancelAnimationFrame(frame);for(const event of ['tileloadstart','tileload','tileerror','load'])layer.off(event,schedule);map.off('movestart',moved);map.off('moveend zoomend resize',schedule)}};
},message(health){return ({LOADING:'도로 지도 불러오는 중…',READY:'도로 지도 표시 중',PARTIAL:'도로 지도 일부 표시 · 나머지 타일을 기다리는 중',UNAVAILABLE:'도로 지도를 불러오지 못했습니다. 네트워크를 확인하고 다시 시도하세요.',OFFLINE:health.loaded>0?'오프라인 · 받아 둔 도로 지도 표시':'오프라인 · 이 지역의 도로 지도를 불러올 수 없습니다.'})[health.status]}};
