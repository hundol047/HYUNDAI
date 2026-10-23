// Deliberately distributed samples, not exhaustive geographical coverage.
const previous=require('./world-drive-cases.cjs');
const extra=[
 ['lisbon','리스본','PT','Europe','pt-PT',[38.7137,-9.1394],[38.7063,-9.1453],'A Brasileira Lisboa'],
 ['seoul','서울','KR','Asia','ko-KR',[37.5663,126.9779],[37.5796,126.9770],'스타벅스 광화문'],
 ['montreal','몬트리올','CA','North America','fr-CA',[45.5018,-73.5736],[45.5088,-73.5540],'Schwartz Montreal'],
 ['marrakesh','마라케시','MA','Africa','ar-MA',[31.6340,-8.0065],[31.6222,-7.9895],'Cafe de France Marrakech'],
 ['santiago','산티아고','CL','South America','es-CL',[-33.4378,-70.6504],[-33.4431,-70.6539],'Cafe Haiti Santiago'],
 ['melbourne','멜버른','AU','Oceania','en-AU',[-37.8165,144.9567],[-37.8136,144.9730],'Market Lane Coffee Melbourne'],
 ['amsterdam','암스테르담','NL','Europe','nl-NL',[52.3740,4.8939],[52.3600,4.8850],'Bocca Coffee Amsterdam'],
 ['osaka','오사카','JP','Asia','ja-JP',[34.7050,135.4970],[34.6930,135.5010],'Starbucks Osaka'],
 ['san-francisco','샌프란시스코','US','North America','en-US',[37.8020,-122.4180],[37.7900,-122.4070],'Blue Bottle Coffee San Francisco'],
 ['accra','아크라','GH','Africa','en-GH',[5.5560,-0.1960],[5.5690,-0.1810],'Cafe Accra'],
 ['lima','리마','PE','South America','es-PE',[-12.1200,-77.0360],[-12.1300,-77.0290],'La Lucha Sangucheria Miraflores'],
 ['wellington','웰링턴','NZ','Oceania','en-NZ',[-41.2840,174.7760],[-41.2970,174.7750],'Fidel Cafe Wellington'],
 ['rome','로마','IT','Europe','it-IT',[41.9000,12.4960],[41.8920,12.4870],'Sant Eustachio Il Caffe Roma'],
 ['taipei','타이베이','TW','Asia','zh-TW',[25.0475,121.5170],[25.0330,121.5290],'Din Tai Fung Taipei'],
 ['vancouver','밴쿠버','CA','North America','en-CA',[49.2820,-123.1200],[49.2900,-123.1300],'Revolver Coffee Vancouver'],
 ['kigali','키갈리','RW','Africa','en-RW',[-1.9440,30.0620],[-1.9560,30.0750],'Question Coffee Kigali'],
 ['rio','리우데자네이루','BR','South America','pt-BR',[-22.9670,-43.1810],[-22.9790,-43.1900],'Confeitaria Colombo Rio de Janeiro'],
 ['hobart','호바트','AU','Oceania','en-AU',[-42.8820,147.3240],[-42.8900,147.3310],'Jackman McRoss Hobart'],
 ['helsinki','헬싱키','FI','Europe','fi-FI',[60.1700,24.9410],[60.1610,24.9510],'Cafe Ekberg Helsinki'],
 ['kuala-lumpur','쿠알라룸푸르','MY','Asia','ms-MY',[3.1470,101.6950],[3.1580,101.7120],'Old Town White Coffee Kuala Lumpur'],
 ['los-angeles','로스앤젤레스','US','North America','en-US',[34.0490,-118.2490],[34.0570,-118.2380],'Grand Central Market Los Angeles'],
 ['dakar','다카르','SN','Africa','fr-SN',[14.6690,-17.4330],[14.6810,-17.4420],'Cafe de Rome Dakar'],
 ['bogota','보고타','CO','South America','es-CO',[4.6000,-74.0750],[4.6120,-74.0700],'Juan Valdez Bogota'],
 ['christchurch','크라이스트처치','NZ','Oceania','en-NZ',[-43.5310,172.6300],[-43.5350,172.6450],'C1 Espresso Christchurch'],
 ['stockholm','스톡홀름','SE','Europe','sv-SE',[59.3320,18.0580],[59.3200,18.0740],'Vete Katten Stockholm'],
 ['jakarta','자카르타','ID','Asia','id-ID',[-6.1860,106.8220],[-6.1960,106.8230],'Starbucks Jakarta'],
 ['honolulu','호놀룰루','US','North America','en-US',[21.2890,-157.8400],[21.2790,-157.8270],'Island Vintage Coffee Waikiki'],
 ['addis-ababa','아디스아바바','ET','Africa','en-ET',[9.0090,38.7540],[9.0200,38.7550],'Tomoca Coffee Addis Ababa'],
 ['quito','키토','EC','South America','es-EC',[-0.2190,-78.5100],[-0.2080,-78.5000],'Cafe Mosaico Quito'],
 ['perth','퍼스','AU','Oceania','en-AU',[-31.9520,115.8520],[-31.9550,115.8680],'London Court Perth']
].map(([id,label,country,continent,language,start,end,query])=>({id,label,country,continent,language,start,end,query}));
// Mix new coverage with previously problematic searches/maneuvers early.
const priority=['toronto','daegu','dubai','sao-paulo','furka','queenstown'];
const first=priority.flatMap((id,i)=>[extra[i],previous.find(s=>s.id===id)]);
module.exports=[...first,...extra.slice(6),...previous.filter(s=>!priority.includes(s.id))];
