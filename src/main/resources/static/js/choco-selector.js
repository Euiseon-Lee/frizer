/* User-defined UI moods. Each usage scope owns an independent shuffle bag. */
(function(root) {
'use strict';
const groups={
 PROFILE:['happy-closeup','back-view-harness'],
 WAITING:['puppy-sit','empty-curious','extra-chin-on-paw','extra-puppy-gaze','pink-coat-look-back'],
 REST:['cozy-curl','extra-curled-smile','leaf-hat-front','leaf-hat-side','rest','sniff',
  'flower-sniff','side-rest','belly-up-lounge','flower-collar-sit','plastic-flower-hat','striped-socks-puppy','snack-ring-tilt',
  'upside-down-gaze','chin-scratch-closeup','lying-blank-gaze','relaxed-smile-portrait'],
 NEUTRAL:['proud-sit','puppy-tilt','puppy-ready','look-aside','calm-closeup','red-collar-puppy','puppy-paw-reach','puppy-look-down','leash-hold'],
 HAPPY:['come-running','extra-sunny-sit','extra-tongue-step','happy-lounge','happy-sit','puppy-front-paws','happy-run-front','belly-up-play','breeze-sly-smile','breeze-happy-smile','side-smile-stand']
};
// Photo keys use the same lowercase kebab-case convention as the asset filenames.
const addedPhotos={
 "belly-up-sprawl":{"emotionGroup":"REST","poseGroup":"belly-up","renderMode":"wide"},
 "sleeping-with-chicken-toy":{"emotionGroup":"REST","poseGroup":"sleeping-side","renderMode":"wide"},
 "belly-up-curl":{"emotionGroup":"REST","poseGroup":"belly-up","renderMode":"wide"},
 "blank-stare":{"emotionGroup":"NEUTRAL","poseGroup":"front-portrait","renderMode":"portrait"},
 "threshold-lounge":{"emotionGroup":"REST","poseGroup":"lying-portrait","renderMode":"portrait"},
 "curled-up-gaze":{"emotionGroup":"REST","poseGroup":"curled","renderMode":"contain"},
 "smiling-sit":{"emotionGroup":"HAPPY","poseGroup":"adult-sit","renderMode":"contain"},
 "puppy-head-tilt-sit":{"emotionGroup":"NEUTRAL","poseGroup":"puppy-sit","renderMode":"contain"},
 "sideways-lounge":{"emotionGroup":"REST","poseGroup":"lying-side","renderMode":"contain"},
 "chicken-begging-side-gaze":{"emotionGroup":"NEUTRAL","poseGroup":"crossed-paws","renderMode":"contain"},
 "belly-up-sleep":{"emotionGroup":"REST","poseGroup":"belly-up-wide","renderMode":"wide"},
 "chicken-begging-front-gaze":{"emotionGroup":"NEUTRAL","poseGroup":"crossed-paws","renderMode":"contain"},
 "happy-drool":{"emotionGroup":"HAPPY","poseGroup":"front-portrait","renderMode":"portrait"},
 "tongue-out-side-sit":{"emotionGroup":"HAPPY","poseGroup":"side-sit","renderMode":"contain"},
 "playful-puppy-approach":{"emotionGroup":"HAPPY","poseGroup":"puppy-portrait","renderMode":"portrait"},
 "puppy-proud-sit":{"emotionGroup":"NEUTRAL","poseGroup":"puppy-sit","renderMode":"contain"},
 "puppy-chin-up-sit":{"emotionGroup":"NEUTRAL","poseGroup":"puppy-sit","renderMode":"contain"},
 "wide-stance-smile":{"emotionGroup":"HAPPY","poseGroup":"smiling-standing","renderMode":"contain"},
 "bandaged-nose":{"emotionGroup":"NEUTRAL","poseGroup":"bandaged-portrait","renderMode":"contain"},
 "walk-ready-side-gaze":{"emotionGroup":"NEUTRAL","poseGroup":"lying-portrait","renderMode":"contain"},
 "walk-ready-front-gaze":{"emotionGroup":"NEUTRAL","poseGroup":"lying-rest","renderMode":"contain"},
 "proud-howl":{"emotionGroup":"HAPPY","poseGroup":"howling","renderMode":"contain"},
 "apple-box-chin-rest":{"emotionGroup":"REST","poseGroup":"box-lounge","renderMode":"contain"},
 "proud-lounge":{"emotionGroup":"HAPPY","poseGroup":"lying-rest","renderMode":"contain"},
 "smiling-lounge":{"emotionGroup":"HAPPY","poseGroup":"lying-rest","renderMode":"wide"},
 "blissful-rest":{"emotionGroup":"REST","poseGroup":"relaxed-portrait","renderMode":"portrait"},
 "handsome-profile-closeup":{"emotionGroup":"NEUTRAL","poseGroup":"side-portrait","renderMode":"portrait"},
 "handsome-profile-lounge":{"emotionGroup":"NEUTRAL","poseGroup":"lying-side","renderMode":"contain"},
 "hopeful-upward-gaze":{"emotionGroup":"HAPPY","poseGroup":"upward-portrait","renderMode":"portrait"},
 "lion-king-puppy":{"emotionGroup":"NEUTRAL","poseGroup":"puppy-held","renderMode":"contain"},
 "walk-ready-head-tilt":{"emotionGroup":"NEUTRAL","poseGroup":"tilt-portrait","renderMode":"portrait"},
 "curious-puppy-gaze":{"emotionGroup":"NEUTRAL","poseGroup":"puppy-portrait","renderMode":"portrait"},
 "chair-sit":{"emotionGroup":"HAPPY","poseGroup":"chair-sit","renderMode":"contain"}
};
for(const [key,photo] of Object.entries(addedPhotos))groups[photo.emotionGroup].push(key);
const wide=['side-rest','belly-up-lounge',...Object.keys(addedPhotos).filter(k=>addedPhotos[k].renderMode==='wide')];
const storageExcluded=['sniff','rest'];
const general=[...groups.REST,...groups.NEUTRAL,...groups.HAPPY];
const scopes={
 PROFILE:groups.PROFILE,
 HEADER:general.filter(k=>!wide.includes(k)&&k!=='puppy-tilt'),
 ERROR:groups.WAITING,
 EMPTY:[...groups.WAITING,...groups.REST],
 SUCCESS:groups.HAPPY,
 CALM:groups.REST,
 OBSERVE:groups.NEUTRAL,
 STORAGE_CALM:groups.REST.filter(k=>!storageExcluded.includes(k)),
 EXPLORE:general.filter(k=>!storageExcluded.includes(k)),
 WIDE:[...new Set([...groups.HAPPY,...wide])],
 PORTRAIT:general.filter(k=>!wide.includes(k)&&!storageExcluded.includes(k))
};
const policies={profile:{scope:'PROFILE',sessionStable:true}};
for(const role of ['home-hero','list-header','detail-header','history-header','error-header','add-header','edit-header'])
 policies[role]={scope:'HEADER',header:true};
for(const role of ['success-create','success-add-stock','success-consume'])policies[role]={scope:'SUCCESS'};
policies.banner={scope:'WIDE'};
for(const role of ['rest','loading'])policies[role]={scope:'CALM'};
for(const role of ['fridge','freezer'])policies[role]={scope:'STORAGE_CALM'};
for(const role of ['warning','success-discard'])policies[role]={scope:'OBSERVE'};
policies.room={scope:'PORTRAIT'};
policies.all={scope:'EXPLORE'};
policies.empty={scope:'EMPTY'};
policies.error={scope:'ERROR',error:true};
const poses={
 'happy-closeup':'front-portrait','empty-curious':'tilt-portrait',rest:'sleep-portrait',sniff:'side-portrait',
 'leaf-hat-front':'leaf-lounge','leaf-hat-side':'leaf-lounge','cozy-curl':'curled','extra-curled-smile':'curled',
 'proud-sit':'adult-sit','look-aside':'adult-sit','happy-sit':'adult-sit','extra-sunny-sit':'adult-sit',
 'happy-lounge':'lying-rest','extra-chin-on-paw':'lying-rest','come-running':'walking','extra-tongue-step':'walking',
 'puppy-sit':'puppy-sit','puppy-tilt':'puppy-sit','puppy-ready':'puppy-sit','extra-puppy-gaze':'puppy-sit',
 'puppy-front-paws':'puppy-front','back-view-harness':'back-portrait','pink-coat-look-back':'look-back',
 'calm-closeup':'front-portrait','happy-run-front':'running-front','flower-sniff':'flower-portrait',
 'red-collar-puppy':'puppy-portrait','puppy-paw-reach':'puppy-reach','belly-up-play':'belly-up',
 'puppy-look-down':'puppy-standing','side-rest':'lying-side','belly-up-lounge':'belly-up-wide',
 'flower-collar-sit':'decorated-sit','plastic-flower-hat':'decorated-portrait',
 'striped-socks-puppy':'puppy-reach','snack-ring-tilt':'decorated-portrait',
 'upside-down-gaze':'upside-down-portrait','chin-scratch-closeup':'petted-portrait','lying-blank-gaze':'lying-portrait',
 'leash-hold':'leash-standing','breeze-sly-smile':'breeze-lounge','breeze-happy-smile':'breeze-lounge',
 'relaxed-smile-portrait':'relaxed-portrait','side-smile-stand':'smiling-standing'
};
const assets={};
for(const [emotionGroup,keys] of Object.entries(groups))for(const key of keys){
 assets[key]={key,emotionGroup,poseGroup:addedPhotos[key]?.poseGroup||poses[key],
  allowedRoles:Object.keys(policies).filter(role=>scopes[policies[role].scope].includes(key)),
  renderMode:addedPhotos[key]?.renderMode||(wide.includes(key)?'wide':key==='sniff'?'left-edge':['happy-closeup','empty-curious','rest','calm-closeup','back-view-harness','upside-down-gaze','chin-scratch-closeup','lying-blank-gaze','relaxed-smile-portrait'].includes(key)?'portrait':'contain')};
}
const validKey=k=>typeof k==='string'&&Object.hasOwn(assets,k);
const validId=id=>typeof id==='string'&&/^[a-z0-9-]{1,80}$/.test(id);
const isEligible=(key,role)=>validKey(key)&&Object.hasOwn(policies,role)&&assets[key].allowedRoles.includes(role);
const candidatesFor=role=>Object.values(assets).filter(a=>isEligible(a.key,role));
const storageKey='frizer.choco.scopes.v4';
function createSelector(storage,random=Math.random){
 const state={version:4,bags:{},profile:null};
 let saved;
 try{saved=JSON.parse(storage?.getItem(storageKey));}catch(_){}
 for(const [scope,keys] of Object.entries(scopes)){
  const bag=saved?.version===4?saved.bags?.[scope]:null;
  // Only trust a complete partition of the current pool; malformed/stale bags reset.
  if(bag&&Array.isArray(bag.remaining)&&Array.isArray(bag.used)){
   const all=[...bag.remaining,...bag.used];
   if(all.length===keys.length&&new Set(all).size===keys.length&&all.every(k=>keys.includes(k)))
    state.bags[scope]={remaining:[...bag.remaining],used:[...bag.used],last:keys.includes(bag.last)?bag.last:null};
  }
 }
 if(groups.PROFILE.includes(saved?.profile))state.profile=saved.profile;
 // Global exposure history has no meaning in the independent scope model.
 try{for(let i=storage.length-1;i>=0;i--){const k=storage.key(i);
  if(k&&(k.startsWith('frizer.choco.global.')||k.startsWith('frizer.choco.empty.')||k==='frizer.choco.scopes.v3'))storage.removeItem(k);
 }}catch(_){}
 const persist=()=>{try{storage.setItem(storageKey,JSON.stringify(state));}catch(_){}};
 function shuffle(keys){
  const result=[...keys];
  for(let i=result.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[result[i],result[j]]=[result[j],result[i]];}
  return result;
 }
 function take(scope,available){
  let bag=state.bags[scope];
  if(!bag)bag=state.bags[scope]={remaining:shuffle(scopes[scope]),used:[],last:null};
  if(!bag.remaining.length){bag.remaining=shuffle(scopes[scope]);bag.used=[];}
  const choices=bag.remaining.filter(k=>available.has(k));
  // Retain blocked entries for the next page. Screen uniqueness/load failures can
  // make every unconsumed entry unavailable: reuse an available entry rather than hide.
  const pool=choices.length?choices:bag.used.filter(k=>available.has(k));
  const key=pool.find(k=>k!==bag.last)||pool[0];
  if(!key)return null;
  const index=bag.remaining.indexOf(key);
  if(index>=0){bag.remaining.splice(index,1);bag.used.push(key);}
  bag.last=key;return key;
 }
 function createPage(){
 const assigned=new Map(),failed=new Map();let previous=new Map();
  function sync(regions){
   const current=new Map(regions.filter(r=>validId(r.id)&&Object.hasOwn(policies,r.role)).map(r=>[r.id,r]));
   for(const [id,r] of previous){const next=current.get(id);
    if(!next||next.role!==r.role){assigned.delete(id);failed.delete(id);}
   }
   previous=current;
   const used=new Set();
   function reserve(key){if(key)used.add(key);}
   function assign(r){
    const pool=failed.get(r.id)?.size>=2?[]:candidatesFor(r.role).filter(a=>!used.has(a.key)&&!failed.get(r.id)?.has(a.key));
    const key=pool.length?take(policies[r.role].scope,new Set(pool.map(a=>a.key))):null;
    assigned.set(r.id,key);reserve(key);
   }
   // Session-stable profile from its own pool; neither image participates elsewhere.
   for(const r of current.values())if(policies[r.role].sessionStable){
    if(!state.profile)state.profile=take('PROFILE',new Set(groups.PROFILE));
    if(failed.get(r.id)?.has(state.profile)&&failed.get(r.id).size<2)
     state.profile=take('PROFILE',new Set(groups.PROFILE.filter(k=>!failed.get(r.id).has(k))));
    const key=state.profile;
    assigned.set(r.id,used.has(key)||failed.get(r.id)?.has(key)?null:key);reserve(key);
   }
   const errors=[...current.values()].filter(r=>policies[r.role].error);
   for(const r of errors){const key=assigned.get(r.id);if(key&&!used.has(key))reserve(key);else assigned.delete(r.id);}
   for(const r of errors)if(!assigned.has(r.id))assign(r);
   // Stable existing regions retain their assignment unless a new error occupies it.
   for(const [id,key] of assigned){const r=current.get(id);
    if(!r||policies[r.role].sessionStable||policies[r.role].error)continue;
    if(!key||used.has(key)){assigned.delete(id);continue;}
    reserve(key);
   }
   const pending=[...current.values()].filter(r=>!assigned.has(r.id));
   function remainingCount(r){
    const bag=state.bags[policies[r.role].scope];
    const keys=bag?.remaining.length?bag.remaining:scopes[policies[r.role].scope];
    return keys.filter(k=>!used.has(k)&&!failed.get(r.id)?.has(k)).length;
   }
   // Allocate nearly exhausted bags first, before broader scopes can occupy their
   // last unseen candidate. Recalculate after each reservation on this page.
   while(pending.length){
    pending.sort((a,b)=>remainingCount(a)-remainingCount(b)||candidatesFor(a.role).length-candidatesFor(b.role).length);
    assign(pending.shift());
   }
   persist();return new Map(assigned);
  }
  return {sync,loadExhausted:id=>(failed.get(id)?.size||0)>=2,fail(id,key){if(assigned.get(id)!==key)return;
   if(!failed.has(id))failed.set(id,new Set());failed.get(id).add(key);assigned.delete(id);
  }};
 }
 persist();return {createPage};
}
root.ChocoSelector={assets,groups,scopes,policies,storageKey,isEligible,candidatesFor,createSelector};
if(typeof module!=='undefined')module.exports=root.ChocoSelector;
})(typeof window==='undefined'?globalThis:window);
