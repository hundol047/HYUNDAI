'use strict';
// Explicit, one-shot microphone sessions. No hotword/background recording or stored transcripts.
(() => {
 let api,bridge,state,recognizer=null,epoch=0,timer=null,listening=false,message='목적지 → 결과 번호 → 안내 시작을 말하세요.',optionKey='',restored=false;
 const $=id=>document.getElementById(id);
 const html=()=>`<div class="nav-voice-controls"><button type="button" id="nav-voice" aria-pressed="false">🎙 음성 길찾기</button><label>음성·길 안내 언어 <select id="nav-language" aria-label="내비게이션 언어"></select></label><details id="nav-language-details"><summary>다른 언어 코드</summary><label>BCP 47 <input id="nav-language-code" maxlength="48" placeholder="en-US / ko-KR / hi-IN"></label><button id="nav-language-apply" type="button">언어 적용</button><p>31개 지역별 안내 번역 내장. 다른 언어는 영어 안내로 대체합니다. 음성 인식·재생은 브라우저/기기의 지원에 따릅니다. 목적지 음성은 제공자의 서버로 전송될 수 있습니다.</p></details></div><p id="nav-voice-status" role="status" aria-live="polite"></p><p class="mini-note" id="nav-voice-help">목적지 → 1번~6번 → 안내 시작 / start navigation. 주행 중: 안내 반복·안내 종료 / repeat · stop navigation. 입력은 30초 후 종료합니다.</p>`;
 function draw(){
  if(!$('nav-voice'))return;
  const label=listening?'■ 음성 입력 취소':'🎙 음성 길찾기';if($('nav-voice').textContent!==label)$('nav-voice').textContent=label;if($('nav-voice').getAttribute('aria-pressed')!==String(listening))$('nav-voice').setAttribute('aria-pressed',String(listening));
  if($('nav-voice-status').textContent!==message)$('nav-voice-status').textContent=message;
  const parked=api?.canPlan;for(const id of ['nav-language','nav-language-code','nav-language-apply'])$(id).disabled=!parked;
  const codes=[...new Set([...(bridge?JSON.parse(bridge.navLanguages()):[]),...(window.speechSynthesis?.getVoices()||[]).map(v=>v.lang),api?.language||'ko-KR'])].sort();
  const key=codes.join('|');if(key!==optionKey){optionKey=key;$('nav-language').replaceChildren(...codes.map(code=>{const o=document.createElement('option');o.value=code;try{o.textContent=new Intl.DisplayNames([code],{type:'language'}).of(code)+' · '+code}catch{o.textContent=code}return o}))}
  $('nav-language').value=api?.language||'ko-KR';
  if(document.activeElement!==$('nav-language-code'))$('nav-language-code').value=api?.language||'ko-KR';
 }
 function cancel(note){epoch++;if(timer)clearTimeout(timer);timer=null;const old=recognizer;recognizer=null;listening=false;try{old?.abort()}catch{}if(note)message=note;draw()}
 async function start(){
  if(listening){cancel('음성 입력을 취소했습니다.');return}
  if(!api||document.hidden)return;
  cancel();const id=epoch;
  const Recognition=window.SpeechRecognition||window.webkitSpeechRecognition;
  if(!Recognition){message='이 브라우저는 음성 인식을 제공하지 않습니다. 지원 브라우저 또는 Android APK를 사용해 주세요.';draw();return}
  if(!api.canPlan){
   let granted=false;try{granted=(await navigator.permissions?.query({name:'microphone'}))?.state==='granted'}catch{}
   if(id!==epoch||document.hidden)return;
   if(!granted){message='주행 중에는 새 마이크 권한 창을 열지 않습니다. 주차 후 마이크 권한을 허용해 주세요.';draw();return}
  }
  try{
   const current=new Recognition();recognizer=current;listening=true;message='듣고 있습니다 · 목적지 또는 짧은 명령을 말하세요.';
   current.lang=api.language;current.continuous=false;current.interimResults=false;current.maxAlternatives=1;
   current.onresult=e=>{
    if(id!==epoch||document.hidden)return;
    const result=e.results[e.resultIndex??0];if(!result||result.isFinal===false)return;
    const answer=result[0],raw=String(answer?.transcript||'').trim().slice(0,180);
    cancel();if(!raw||answer.confidence>0&&answer.confidence<0.4){message='말씀을 확실히 인식하지 못했습니다. 다시 말씀해 주세요.';draw();return}
    message='인식: '+raw;draw();api.voiceCommand(raw);
   };
   current.onerror=e=>{if(id!==epoch)return;cancel(({ 'not-allowed':'마이크 권한이 거부됐습니다. 주차 후 사이트 권한에서 허용해 주세요.','service-not-allowed':'기기 음성 인식 서비스가 허용되지 않았습니다.','language-not-supported':'선택 언어의 음성 인식을 지원하지 않습니다. 언어/기기 설정을 확인하세요.','audio-capture':'마이크를 사용할 수 없습니다. 기기 연결을 확인하세요.','network':'음성 인식 서버에 연결하지 못했습니다.','no-speech':'말씀을 인식하지 못했습니다. 다시 시도해 주세요.'})[e.error]||'음성 인식이 완료되지 않았습니다. 다시 시도해 주세요.')};
   current.onend=()=>{if(id===epoch)cancel('음성 입력이 종료됐습니다. 다시 눌러 말씀해 주세요.')};
   timer=setTimeout(()=>{if(id===epoch)cancel('음성 입력 시간이 지났습니다. 다시 눌러 말씀해 주세요.')},30000);
   api.pauseVoice();window.speechSynthesis?.cancel();current.start();draw();
  }catch{if(id===epoch)cancel('음성 인식을 시작하지 못했습니다. HTTPS·마이크·기기 설정을 확인하세요.')}
 }
 function language(tag){if(!api?.canPlan)return;cancel();if(!api.setLanguage(tag)){message='올바른 언어 코드를 입력하세요. 예: ko-KR, en-US';draw();return}try{localStorage.setItem('cabinos.navigation.language',tag)}catch{}message=api.instructionLanguage==='en'&&!tag.toLowerCase().startsWith('en')?'선택 언어의 안내 번역 없음 · 영어 안내로 대체. 목적지 음성 인식은 선택 언어 사용.':'언어를 적용했습니다. 음성 엔진/언어팩의 실제 지원이 필요합니다.';draw()}
 document.addEventListener('click',e=>{if(e.target.closest('#nav-voice'))start();if(e.target.closest('#nav-language-apply'))language($('nav-language-code').value.trim())});
 document.addEventListener('change',e=>{if(e.target.id==='nav-language')language(e.target.value)});
 document.addEventListener('visibilitychange',()=>{if(document.hidden)cancel('화면 밖에서는 마이크를 중지합니다.')});
 window.EchoVoice={html,update(a,b,s){api=a;bridge=b;state=s;if(!restored&&api.canPlan){restored=true;try{const saved=localStorage.getItem('cabinos.navigation.language');if(saved)api.setLanguage(saved)}catch{}}draw()},cancel,get listening(){return listening},notice(text){message=text;draw()},reset(){cancel();message='목적지 → 결과 번호 → 안내 시작을 말하세요.';try{localStorage.removeItem('cabinos.navigation.language')}catch{}draw()}};
})();
