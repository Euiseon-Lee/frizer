/* UI mood is user-defined, independent of pose and composition constraints. */
(function(root) {
'use strict';
const policies = {
 profile:{header:true,tiers:[['HAPPY']],preferred:['happy-closeup']},
 'home-hero':{header:true,tiers:[['HAPPY'],['NEUTRAL']],preferred:['happy-lounge','happy-sit','proud-sit']},
 'list-header':{header:true,tiers:[['NEUTRAL'],['HAPPY']]},
 'detail-header':{header:true,tiers:[['NEUTRAL'],['HAPPY']]},
 'history-header':{header:true,tiers:[['NEUTRAL'],['HAPPY']]},
 'error-header':{header:true,tiers:[['NEUTRAL'],['HAPPY']]},
 'add-header':{header:true,tiers:[['HAPPY'],['NEUTRAL']],preferred:['come-running','puppy-front-paws']},
 'edit-header':{header:true,tiers:[['NEUTRAL'],['HAPPY']]},
 warning:{tiers:[['NEUTRAL']]}, rest:{tiers:[['REST']]},
 empty:{tiers:[['WAITING'],['NEUTRAL']]}, error:{tiers:[['WAITING']],error:true},
 'success-create':{tiers:[['HAPPY'],['NEUTRAL']]},
 'success-consume':{tiers:[['HAPPY'],['NEUTRAL']]},
 'success-discard':{tiers:[['NEUTRAL']]},
 banner:{tiers:[['HAPPY','WAITING'],['NEUTRAL']]},
 room:{tiers:[['NEUTRAL','HAPPY'],['WAITING']]},
 fridge:{tiers:[['REST'],['NEUTRAL','WAITING']],preferred:['leaf-hat-front','leaf-hat-side']},
 freezer:{tiers:[['REST'],['NEUTRAL','WAITING']],preferred:['cozy-curl','extra-curled-smile']},
 all:{tiers:[['HAPPY'],['NEUTRAL'],['WAITING']]},
 loading:{tiers:[['REST']],preferred:['sniff']}
};
const headers=['home-hero','list-header','detail-header','history-header','error-header','add-header','edit-header'];
const bodies=['warning','rest','room','fridge','freezer','all','success-create','success-consume','success-discard','banner','empty','error'];
const fullBody=[...headers,...bodies];
const assets={};
function add(key,emotionGroup,poseGroup,allowedRoles=fullBody,renderMode='contain'){
 assets[key]={key,emotionGroup,poseGroup,allowedRoles:[...allowedRoles],renderMode};
}
add('empty-curious','WAITING','tilt-portrait',['error'],'portrait');
add('rest','REST','sleep-portrait',['rest'],'portrait');
add('sniff','REST','side-portrait',['loading'],'left-edge');
add('happy-closeup','HAPPY','front-portrait',['profile'],'portrait');
add('leaf-hat-front','REST','leaf-lounge',['fridge']);
add('leaf-hat-side','REST','leaf-lounge',['fridge']);
add('proud-sit','NEUTRAL','adult-sit');
add('look-aside','HAPPY','adult-sit');
add('happy-sit','HAPPY','adult-sit');
add('happy-lounge','HAPPY','lying-rest');
add('puppy-sit','WAITING','puppy-sit',['empty','error']);
add('puppy-tilt','NEUTRAL','puppy-sit');
add('puppy-ready','NEUTRAL','puppy-sit');
add('cozy-curl','REST','curled',bodies);
add('come-running','HAPPY','walking',['add-header','edit-header','success-create','success-consume','banner']);
add('puppy-front-paws','HAPPY','puppy-front',['add-header','edit-header','success-create','success-consume','banner','all']);
add('extra-chin-on-paw','WAITING','lying-rest',['empty','error','banner','room','fridge','freezer','all']);
add('extra-curled-smile','REST','curled',bodies);
add('extra-sunny-sit','HAPPY','adult-sit');
add('extra-tongue-step','HAPPY','walking');
add('extra-puppy-gaze','WAITING','puppy-sit',['empty','error','banner','room','fridge','freezer','all']);
const preferred=Object.fromEntries(Object.entries(policies).map(([role,p])=>[role,p.preferred||[]]));
const validKey=k=>typeof k==='string' && Object.hasOwn(assets,k);
const validId=id=>typeof id==='string' && /^[a-z0-9-]{1,80}$/.test(id);
function isEligible(key,role){
 if(!validKey(key)||!Object.hasOwn(policies,role))return false;
 const a=assets[key],p=policies[role];
 return a.allowedRoles.includes(role) && !(p.header&&a.emotionGroup==='WAITING') &&
   !(key==='happy-closeup'&&role!=='profile') && p.tiers.some(t=>t.includes(a.emotionGroup));
}
const candidatesFor=role=>Object.values(assets).filter(a=>isEligible(a.key,role));
const storageKey='frizer.choco.global.v2';
function createSelector(storage,random=Math.random){
 let state={recent:[],last:{}};
 try{
  // Migrate exposure preferences, never cached page assignments.
  const saved=JSON.parse(storage.getItem(storageKey)||storage.getItem('frizer.choco.global.v1'));
  if(saved&&Array.isArray(saved.recent)){
   state.recent=saved.recent.filter(k=>validKey(k)&&k!=='happy-closeup').slice(-80);
   if(saved.last&&typeof saved.last==='object')for(const [id,key] of Object.entries(saved.last))
    if(validId(id)&&validKey(key)&&key!=='happy-closeup')state.last[id]=key;
  }
 }catch(_){}
 try{
  for(let i=storage.length-1;i>=0;i--){const k=storage.key(i);
   if(k&&(k.startsWith('frizer.choco.empty.')||k.startsWith('frizer.choco.global.'))&&k!==storageKey)storage.removeItem(k);
  }
 }catch(_){}
 const persist=()=>{try{storage.setItem(storageKey,JSON.stringify(state));}catch(_){}};
 persist();
 function createPage(){
  const assigned=new Map(),failed=new Map();let previous=new Map();
  function sync(regions){
   const current=new Map(regions.filter(r=>validId(r.id)&&Object.hasOwn(policies,r.role)).map(r=>[r.id,r]));
   for(const [id,r] of previous){const next=current.get(id);
    if(!next||next.role!==r.role||(next.fixed||null)!==(r.fixed||null)){assigned.delete(id);failed.delete(id);}
   }
   previous=current;
   for(const [id,a] of assigned)if(a.key&&!isEligible(a.key,current.get(id)?.role))assigned.delete(id);
   const used=new Set(),groups=new Set();
   function reserve(key){if(key){used.add(key);groups.add(assets[key].poseGroup);}}
   function choose(r){
    const available=candidatesFor(r.role).filter(a=>!used.has(a.key)&&!failed.get(r.id)?.has(a.key));
    // Mood tier is strict: history or preferred poses cannot promote a lower tier.
    const tier=policies[r.role].tiers.find(t=>available.some(a=>t.includes(a.emotionGroup)));
    if(!tier)return null;
    const scored=available.filter(a=>tier.includes(a.emotionGroup)).map(a=>({a,score:[
     groups.has(a.poseGroup)?1:0,state.last[r.id]===a.key?1:0,
     state.recent.filter(k=>k===a.key).length,preferred[r.role].includes(a.key)?0:1,random()
    ]})).sort((a,b)=>{for(let i=0;i<a.score.length;i++)if(a.score[i]!==b.score[i])return a.score[i]-b.score[i];return 0;});
    return scored[0].a.key;
   }
   function assign(r){
    const key=choose(r);assigned.set(r.id,{key});reserve(key);
    if(key){state.recent.push(key);state.recent=state.recent.slice(-80);state.last[r.id]=key;}
   }
   // Profile stays occupied even if its resource fails to load; it is never counted.
   for(const r of current.values())if(r.fixed){
    const key=isEligible(r.fixed,r.role)&&!used.has(r.fixed)?r.fixed:null;
    assigned.set(r.id,{key});reserve(key);
   }
   const errors=[...current.values()].filter(r=>policies[r.role].error&&!r.fixed);
   // Existing errors stay stable; new errors may take a key from another body region.
   for(const r of errors){const a=assigned.get(r.id);if(a?.key&&!used.has(a.key))reserve(a.key);else if(a?.key)assigned.delete(r.id);}
   for(const r of errors)if(!assigned.has(r.id))assign(r);
   for(const [id,a] of assigned){const r=current.get(id);
    if(!r||r.fixed||policies[r.role].error)continue;
    if(a.key&&used.has(a.key)){assigned.delete(id);continue;}
    reserve(a.key);
   }
   const pending=[...current.values()].filter(r=>!assigned.has(r.id))
    .sort((a,b)=>candidatesFor(a.role).length-candidatesFor(b.role).length);
   for(const r of pending)assign(r);
   persist();return new Map([...assigned].map(([id,a])=>[id,a.key]));
  }
  return {sync,fail(id,key){if(assigned.get(id)?.key!==key)return;
   if(!failed.has(id))failed.set(id,new Set());failed.get(id).add(key);assigned.delete(id);
  }};
 }
 return {createPage};
}
root.ChocoSelector={assets,policies,preferred,storageKey,isEligible,candidatesFor,createSelector};
if(typeof module!=='undefined')module.exports=root.ChocoSelector;
})(typeof window==='undefined'?globalThis:window);
