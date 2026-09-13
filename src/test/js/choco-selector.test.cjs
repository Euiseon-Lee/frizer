const assert=require('node:assert/strict'), fs=require('node:fs'), vm=require('node:vm');
const core=require('../../main/resources/static/js/choco-selector.js');
const {assets,preferred,storageKey,createSelector}=core;
function storage(){
 const data=new Map();return {get length(){return data.size},key:i=>[...data.keys()][i],
 getItem:k=>data.get(k)||null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};
}
const profile={id:'profile',role:'profile',fixed:'happy-closeup'};
const region=(role,id=role)=>({id,role});
const home=state=>[profile,...['home-hero','room','fridge','freezer','all','banner'].map(r=>region(r)),
 region(state==='warning'?'warning':state==='empty'?'empty':'rest','home-summary')];
function valid(regions,result){
 const chosen=[...result.values()].filter(Boolean);
 assert.equal(new Set(chosen).size,chosen.length,'same-page duplicate');
 for(const r of regions){const k=result.get(r.id);if(k)assert.ok(core.isEligible(k,r.role));}
}
const catalog=[...JSON.parse(fs.readFileSync('docs/ui-v5/asset-map.json')).assets,
 ...JSON.parse(fs.readFileSync('docs/ui-v5-addon/asset-map-addon.json')).assets];
for(const a of Object.values(assets)){
 assert.ok(catalog.some(x=>x.key===a.key&&x.tier==='A'));
 assert.ok(fs.existsSync('src/main/resources/static/assets/choco/'+a.key+'.png'));
}
assert.deepEqual(assets['empty-curious'].allowedRoles,['error']);
assert.deepEqual(assets['puppy-sit'].allowedRoles,['empty','error']);
assert.deepEqual(assets['happy-closeup'].allowedRoles,['profile']);
for(let i=0;i<100;i++){
 const saved=storage(), selector=createSelector(saved);
 for(const state of ['warning','rest','empty']){
  const page=selector.createPage(), regions=home(state), first=page.sync(regions);
  valid(regions,first);
  const history=saved.getItem(storageKey);
  assert.deepEqual(page.sync(regions),first);assert.equal(saved.getItem(storageKey),history);
  const extended=[...regions,region('success-create')], added=page.sync(extended);
  for(const [id,key] of first)assert.equal(added.get(id),key);
  valid(extended,added);
  page.sync([profile]); // release dynamic occupancy, preserve exposure history
  valid(regions,page.sync(regions));
 }
}
const saved=storage(),selector=createSelector(saved);
const sequence=['home-hero','list-header','detail-header','add-header','history-header','empty','home-hero'];
let previous;
for(let i=0;i<50;i++){
 for(const role of sequence){
  const regions=[profile,region(role)];
  const result=selector.createPage().sync(regions);valid(regions,result);
 }
}
const history=JSON.parse(saved.getItem(storageKey));
assert.ok(!history.recent.includes('happy-closeup'));
assert.ok(history.recent.length<=80);
assert.ok(history.recent.some(k=>k.startsWith('extra-')));
const reload=createSelector(saved,()=>.5).createPage().sync([profile,region('home-hero')]);
assert.notEqual(reload.get('home-hero'),history.last['home-hero']);
// Empty and general roles consume the very same history.
const shared=storage(), sharedSelector=createSelector(shared);
sharedSelector.createPage().sync([profile,region('empty')]);
assert.equal(JSON.parse(shared.getItem(storageKey)).recent.length,1);
sharedSelector.createPage().sync([profile,region('list-header')]);
assert.equal(JSON.parse(shared.getItem(storageKey)).recent.length,2);
const blockedStorage=new Proxy({}, {get(){throw Error('disabled')}});
valid(home('warning'),createSelector(blockedStorage).createPage().sync(home('warning')));
const bad=storage();bad.setItem('frizer.choco.empty.v5.1.a5','old');
bad.setItem(storageKey,JSON.stringify({recent:['invalid','happy-closeup','look-aside'],last:{old:'invalid'}}));
createSelector(bad);assert.equal(bad.getItem('frizer.choco.empty.v5.1.a5'),null);
assert.deepEqual(JSON.parse(bad.getItem(storageKey)).recent,['look-aside']);
const exhausted=createSelector(storage()).createPage(), many=[profile,...Array.from({length:30},(_,i)=>region('warning','warning-'+i))];
const exhaustedResult=exhausted.sync(many);valid(many,exhaustedResult);
assert.ok([...exhaustedResult.values()].includes(null));
const narrow=createSelector(storage()).createPage(), narrowRegions=[profile,region('loading','loading-one'),region('loading','loading-two')];
assert.equal([...narrow.sync(narrowRegions).values()].filter(k=>k==='sniff').length,1);
const failurePage=createSelector(storage()).createPage(), failRegions=[profile,region('warning')];
let result=failurePage.sync(failRegions), attempts=0;
while(result.get('warning')){
 assert.ok(++attempts<=Object.keys(assets).length);
 failurePage.fail('warning',result.get('warning'));result=failurePage.sync(failRegions);
}
assert.equal(result.get('warning'),null);
// Production DOM adapter: no exposure increment on input/rerender, recreated node,
// mutation deliveries, resize; new regions only; removed regions release occupancy.
const domStore=storage(), events={}; let observer;
function img(id,role,fixed){
 let src='';const attrs={};
 return {dataset:{chocoRegion:id,chocoRole:role,chocoBase:'/assets/choco/',...(fixed?{chocoFixed:fixed}:{})},
 hidden:!fixed,parentElement:{getClientRects:()=>[{}],closest:()=>null},
 classList:{contains(){return false},toggle(){},remove(){}},get src(){return src},set src(v){src=v},
 removeAttribute:k=>{if(k==='src')src=''},getAttribute:k=>attrs[k]};
}
let images=[img('profile','profile','happy-closeup'),img('list-header','list-header')];
const tasks=[];
const sandbox={URL,Map,Set,Math,console,location:{href:'http://localhost/inventory',pathname:'/inventory',search:''},
 sessionStorage:domStore,document:{body:{},querySelectorAll:()=>images},
 queueMicrotask:fn=>tasks.push(fn),MutationObserver:class{constructor(fn){observer=fn}observe(){}},
 addEventListener:(name,fn)=>events[name]=fn};
sandbox.window=sandbox;vm.createContext(sandbox);
const flush=()=>{while(tasks.length)tasks.shift()()};
vm.runInContext(fs.readFileSync('src/main/resources/static/js/choco-selector.js','utf8'),sandbox);
const adapter=fs.readFileSync('src/main/resources/static/js/choco.js','utf8');
vm.runInContext(adapter,sandbox);
const firstSrc=images[1].src,initialHistory=domStore.getItem(storageKey);
observer();observer();flush();vm.runInContext(adapter,sandbox);
assert.equal(images[1].src,firstSrc);assert.equal(domStore.getItem(storageKey),initialHistory);
images[1]=img('list-header','list-header');observer();flush();assert.equal(images[1].src,firstSrc);
images.push(img('list-empty','empty'));observer();flush();
assert.equal(images[1].src,firstSrc);assert.notEqual(images[2].src,firstSrc);
assert.equal(JSON.parse(domStore.getItem(storageKey)).recent.length,2);
images.pop();observer();flush();
images.push(img('list-empty','empty'));observer();flush();
assert.equal(JSON.parse(domStore.getItem(storageKey)).recent.length,3);
const beforeRestore=JSON.parse(domStore.getItem(storageKey)).recent.length;
events.pageshow({persisted:true});flush();assert.equal(JSON.parse(domStore.getItem(storageKey)).recent.length,beforeRestore+2);
images[1].onerror();flush();assert.ok(images[1].src);
console.log('PASS: registry constraints, 300 home allocations, 350 navigations, shared history, reload, stability, state changes, exhaustion and failure handling');// Final mood policy: exact user-defined groups, strict primary tiers and hard limits.
const expected={WAITING:['puppy-sit','empty-curious','extra-chin-on-paw','extra-puppy-gaze'],REST:['rest','sniff','leaf-hat-front','leaf-hat-side','cozy-curl','extra-curled-smile'],NEUTRAL:['proud-sit','puppy-tilt','puppy-ready'],HAPPY:['happy-closeup','happy-lounge','come-running','look-aside','happy-sit','puppy-front-paws','extra-sunny-sit','extra-tongue-step']};
for(const [mood,keys] of Object.entries(expected))for(const key of keys)assert.equal(assets[key].emotionGroup,mood);
for(const [role,p] of Object.entries(core.policies)){
 if(p.header)assert.ok(core.candidatesFor(role).every(a=>a.emotionGroup!=='WAITING'));
}
assert.deepEqual(core.candidatesFor('error').map(a=>a.key).sort(),expected.WAITING.slice().sort());
for(const role of ['warning','success-discard'])assert.ok(core.candidatesFor(role).every(a=>a.emotionGroup==='NEUTRAL'));
assert.ok(core.candidatesFor('rest').every(a=>a.emotionGroup==='REST'));
assert.equal(core.isEligible('proud-sit','rest'),false);
assert.equal(core.isEligible('sniff','rest'),false);
assert.equal(core.isEligible('puppy-sit','banner'),false);
for(const role of ['home-hero','list-header','detail-header','history-header','add-header','edit-header','empty','rest','warning','success-create','success-consume','success-discard']){
 const s=storage();const primary=core.policies[role].tiers[0];
 const keys=core.candidatesFor(role).filter(a=>primary.includes(a.emotionGroup)).map(a=>a.key);
 // Even maximal recent exposure cannot make a secondary mood win.
 s.setItem(storageKey,JSON.stringify({recent:Array.from({length:80},(_,i)=>keys[i%keys.length]),last:{[role]:keys[0]}}));
 const chosen=createSelector(s).createPage().sync([profile,region(role)]).get(role);
 assert.ok(primary.includes(assets[chosen].emotionGroup),role);
}
const observed={empty:new Set(),error:new Set(),banner:new Set()};
const moodsSelector=createSelector(storage());
for(let i=0;i<100;i++)for(const role of Object.keys(observed)){
 const choices=moodsSelector.createPage().sync([profile,region(role)]);valid([profile,region(role)],choices);
 observed[role].add(choices.get(role));
}
assert.deepEqual([...observed.error].sort(),expected.WAITING.slice().sort());
assert.deepEqual([...observed.empty].sort(),['puppy-sit','extra-chin-on-paw','extra-puppy-gaze'].sort());
const bannerRotation=createSelector(storage()),bannerSeen=new Set();
for(let i=0;i<40;i++)bannerSeen.add(bannerRotation.createPage().sync([profile,region('banner')]).get('banner'));
assert.ok(bannerSeen.has('extra-chin-on-paw')&&bannerSeen.has('extra-puppy-gaze'));
// Mood fallback only after every primary candidate is occupied on this page.
const emptyMany=Array.from({length:6},(_,i)=>region('empty','empty-'+i));
const emptyChoices=createSelector(storage()).createPage().sync([profile,...emptyMany]);
assert.ok(emptyMany.slice(0,3).every(r=>assets[emptyChoices.get(r.id)].emotionGroup==='WAITING'));
assert.ok(emptyMany.slice(3).every(r=>assets[emptyChoices.get(r.id)].emotionGroup==='NEUTRAL'));
assert.equal([...exhaustedResult.values()].filter(k=>k&&k!=='happy-closeup').length,3);
// A newly appearing error claims its candidate before other body images, evicting a collision.
const collisionStore=storage(),collisionPage=createSelector(collisionStore).createPage();
const collisionRegions=[profile,...Array.from({length:3},(_,i)=>region('empty','body-'+i))];
const beforeCollision=collisionPage.sync(collisionRegions);
// Fail the portrait so that the error must claim one of the three occupied WAITING keys.
const withError=[...collisionRegions,region('error')];
let afterCollision=collisionPage.sync(withError);
if(afterCollision.get('error')==='empty-curious'){
 collisionPage.fail('error','empty-curious');afterCollision=collisionPage.sync(withError);
}
assert.ok([...beforeCollision.values()].includes(afterCollision.get('error')));
valid(withError,afterCollision);
assert.equal(assets[afterCollision.get('error')].emotionGroup,'WAITING');
const stableCollision=collisionStore.getItem(storageKey);
assert.deepEqual(collisionPage.sync(withError),afterCollision);assert.equal(collisionStore.getItem(storageKey),stableCollision);
// Error failures exhaust WAITING only, never loop or turn into a different mood.
const errorPage=createSelector(storage()).createPage(),errorRegions=[profile,region('error')];
let errorResult=errorPage.sync(errorRegions);const attemptsSeen=new Set();
while(errorResult.get('error')){const key=errorResult.get('error');assert.equal(assets[key].emotionGroup,'WAITING');assert.ok(!attemptsSeen.has(key));attemptsSeen.add(key);errorPage.fail('error',key);errorResult=errorPage.sync(errorRegions);}
assert.equal(attemptsSeen.size,4);assert.deepEqual(errorPage.sync(errorRegions),errorResult);
// A v1 last choice is an exposure preference, not a reusable assignment under v2.
const legacy=storage();legacy.setItem('frizer.choco.global.v1',JSON.stringify({recent:['extra-puppy-gaze','unknown','happy-closeup'],last:{'list-header':'extra-puppy-gaze'}}));
const migrated=createSelector(legacy).createPage().sync([profile,region('list-header')]);
assert.equal(legacy.getItem('frizer.choco.global.v1'),null);
assert.equal(assets[migrated.get('list-header')].emotionGroup,'NEUTRAL');
assert.ok(JSON.parse(legacy.getItem(storageKey)).recent.includes('extra-puppy-gaze'));
console.log('PASS: exact mood groups, strict tiers, all headers, WAITING-only errors, rotation, banner WAITING, new-error collision priority, bounded failures and v1 migration');
