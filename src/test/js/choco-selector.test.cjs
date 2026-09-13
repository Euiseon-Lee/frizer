const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const core=require('../../main/resources/static/js/choco-selector.js');
const {assets,groups,scopes,policies,storageKey,createSelector}=core;
const crypto=require('node:crypto');
function storage(){const data=new Map();return {get length(){return data.size},key:i=>[...data.keys()][i],getItem:k=>data.get(k)||null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};}
function rng(seed=12345){return ()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296};}
const profile={id:'profile',role:'profile',fixed:'happy-closeup'};
const region=(role,id=role)=>({id,role});
const home=state=>[profile,...['home-hero','room','fridge','freezer','all','banner'].map(r=>region(r)),region(state==='warning'?'warning':state==='empty'?'empty':'rest','home-summary')];
function valid(regions,result,complete=true){
 const keys=[...result.values()].filter(Boolean);assert.equal(new Set(keys).size,keys.length,'same-page duplicate');
 for(const r of regions){const key=result.get(r.id);if(complete)assert.ok(key,'missing '+r.id);if(key)assert.ok(core.isEligible(key,r.role),r.role+': '+key);}
}
const originalGroups={PROFILE:['happy-closeup'],WAITING:['puppy-sit','empty-curious','extra-chin-on-paw','extra-puppy-gaze'],REST:['cozy-curl','extra-curled-smile','leaf-hat-front','leaf-hat-side','rest','sniff'],NEUTRAL:['proud-sit','puppy-tilt','puppy-ready','look-aside'],HAPPY:['come-running','extra-sunny-sit','extra-tongue-step','happy-lounge','happy-sit','puppy-front-paws']};
const expected={PROFILE:[...originalGroups.PROFILE,'back-view-harness'],WAITING:[...originalGroups.WAITING,'pink-coat-look-back'],REST:[...originalGroups.REST,'flower-sniff','side-rest','belly-up-lounge','flower-collar-sit','plastic-flower-hat','striped-socks-puppy','snack-ring-tilt'],NEUTRAL:[...originalGroups.NEUTRAL,'calm-closeup','red-collar-puppy','puppy-paw-reach','puppy-look-down'],HAPPY:[...originalGroups.HAPPY,'happy-run-front','belly-up-play']};
assert.deepEqual(groups,expected);assert.equal(Object.keys(assets).length,36);
// Complete ZIP accounting, no duplicate copies, no original overwrite and actual registry links.
const addition=JSON.parse(fs.readFileSync('docs/ui-v5-addon/image-add/results.json','utf8'));
assert.equal(addition.images.length,20);assert.equal(addition.added,15);assert.equal(addition.reused,5);assert.equal(addition.missing,0);
assert.equal(new Set(addition.images.map(r=>r.zip_filename)).size,20);
const sourceRows=fs.readFileSync('docs/ui-v5-addon/image-add/image-mapping.csv','utf8').trim().split(/\r?\n/).slice(1);
assert.equal(sourceRows.length,20);
for(const line of sourceRows){const [original,zip]=line.split(',');assert.ok(addition.images.some(r=>r.original_filename===original&&r.zip_filename===zip));}
const digest=name=>crypto.createHash('sha256').update(fs.readFileSync('src/main/resources/static/assets/choco/'+name)).digest('hex');
for(const [name,hash] of Object.entries(addition.existing_asset_sha256))assert.equal(digest(name),hash,'original changed '+name);
for(const row of addition.images){assert.ok(['ADDED','REUSED'].includes(row.status));assert.ok(assets[row.project_filename.replace(/\.png$/,'')]);assert.equal(digest(row.project_filename),row.project_sha256);if(row.status==='ADDED')assert.equal(row.project_sha256,row.source_sha256);else{assert.ok(row.comparison_mse<0.00001);assert.ok(!fs.existsSync('src/main/resources/static/assets/choco/'+row.zip_filename));}}
assert.equal(addition.images.filter(r=>r.status==='ADDED').length,15);assert.equal(addition.images.filter(r=>r.status==='REUSED').length,5);
assert.equal(addition.images.find(r=>r.zip_filename==='happy-run-front.png').status,'ADDED');
const templateFiles=['home.html','inventory/list.html','inventory/detail.html','inventory/new.html','history/list.html','error.html','fragments/shell.html'];
const templates=templateFiles.map(f=>fs.readFileSync('src/main/resources/templates/'+f,'utf8')).join('\n');
const liveRoles=new Set([...templates.matchAll(/data-choco-role="([a-z-]+)"/g)].map(m=>m[1]));
for(const role of ['home-hero','empty','rest','add-header','edit-header']){assert.ok(templates.includes("'"+role+"'")||templates.includes('"'+role+'"'));liveRoles.add(role);}
for(const a of Object.values(assets)){
 assert.ok(fs.existsSync('src/main/resources/static/assets/choco/'+a.key+'.png'));
 assert.ok([...liveRoles].some(role=>core.isEligible(a.key,role)),a.key+' has no live use');
}
for(const [role,p] of Object.entries(policies))if(p.header){
 assert.equal(core.candidatesFor(role).length,26);
 assert.equal(core.isEligible('puppy-tilt',role),false);
 for(const key of expected.WAITING)assert.equal(core.isEligible(key,role),false);
 for(const key of [...originalGroups.REST,...originalGroups.NEUTRAL,...originalGroups.HAPPY].filter(k=>k!=='puppy-tilt'))assert.ok(core.isEligible(key,role));
 for(const key of ['flower-sniff','flower-collar-sit','plastic-flower-hat','snack-ring-tilt','striped-socks-puppy','calm-closeup'])assert.ok(core.isEligible(key,role));
}
assert.deepEqual(core.candidatesFor('profile').map(a=>a.key),['happy-closeup','back-view-harness']);
assert.equal(assets['calm-closeup'].emotionGroup,'NEUTRAL');
for(const key of expected.PROFILE)assert.deepEqual(assets[key].allowedRoles,['profile']);
// Preserve original roles except for the user's explicit region exclusions.
const oldPools={HEADER:[...originalGroups.REST,...originalGroups.NEUTRAL,...originalGroups.HAPPY],ERROR:originalGroups.WAITING,EMPTY:[...originalGroups.WAITING,...originalGroups.REST],SUCCESS:originalGroups.HAPPY,CALM:originalGroups.REST,OBSERVE:originalGroups.NEUTRAL,EXPLORE:[...originalGroups.REST,...originalGroups.NEUTRAL,...originalGroups.HAPPY]};
for(const [scope,keys] of Object.entries(oldPools)){
 const roles=Object.keys(policies).filter(r=>policies[r].scope===scope);
 if(scope==='SUCCESS')roles.push('banner');if(scope==='EXPLORE')roles.push('room');
 for(const role of roles)for(const key of keys){
  if(['sniff','rest'].includes(key)&&['room','fridge','freezer','all'].includes(role))continue;
  if(key==='puppy-tilt'&&policies[role].header)continue;
  assert.ok(core.isEligible(key,role),'original role removed: '+role+' '+key);
 }
}
for(const role of ['room','all','warning'])assert.equal(core.isEligible('puppy-tilt',role),true);
for(const key of ['sniff','rest']){
 for(const role of ['room','fridge','freezer','all'])assert.equal(core.isEligible(key,role),false);
 for(const role of ['home-hero','list-header','rest','empty'])assert.equal(core.isEligible(key,role),true);
}
assert.deepEqual(core.candidatesFor('error').map(a=>a.key).sort(),[...expected.WAITING].sort());
for(const role of ['warning','success-discard'])assert.ok(core.candidatesFor(role).every(a=>a.emotionGroup==='NEUTRAL'));
// Each independent bag exhausts its exact pool before repeating, across selector reloads.
for(const [scope,keys] of Object.entries(scopes)){
 if(scope==='PROFILE')continue;
 const roles=Object.keys(policies).filter(r=>policies[r].scope===scope),saved=storage(),random=rng(67);
 let last;
 for(let cycle=0;cycle<12;cycle++){
  const seen=new Set();
  for(let i=0;i<keys.length;i++){
   const role=roles[i%roles.length];
   const result=createSelector(saved,random).createPage().sync([profile,region(role)]);
   valid([profile,region(role)],result);const key=result.get(role);
   assert.ok(!seen.has(key),scope+' repeated within cycle');if(last)assert.notEqual(key,last,scope+' boundary repeat');
   seen.add(key);last=key;
  }
  assert.deepEqual([...seen].sort(),[...keys].sort());
 }
}
const independent=storage(),independentSelector=createSelector(independent,rng());
independentSelector.createPage().sync([profile,region('list-header')]);
const headerBag=JSON.parse(independent.getItem(storageKey)).bags.HEADER;
for(const role of ['error','empty','rest','success-create','warning','room'])for(let i=0;i<12;i++)independentSelector.createPage().sync([profile,region(role)]);
assert.deepEqual(JSON.parse(independent.getItem(storageKey)).bags.HEADER,headerBag);
assert.ok(expected.PROFILE.includes(JSON.parse(independent.getItem(storageKey)).profile));
// Real page combinations: reach all 36 images, preserve assignments and never omit due to history.
const saved=storage(),selector=createSelector(saved,rng()),seen=new Set();
for(let i=0;i<120;i++)for(const state of ['warning','rest','empty']){
 const regions=home(state),page=selector.createPage(),first=page.sync(regions);valid(regions,first);
 first.forEach(key=>seen.add(key));const snapshot=saved.getItem(storageKey);
 assert.deepEqual(page.sync(regions),first);assert.equal(saved.getItem(storageKey),snapshot);
 const extended=[...regions,region('success-create')],added=page.sync(extended);valid(extended,added);
 first.forEach((key,id)=>assert.equal(added.get(id),key));page.sync([profile]);valid(regions,page.sync(regions));
}
for(let i=0;i<5;i++)selector.createPage().sync([profile,region('error')]).forEach(key=>seen.add(key));
for(const key of expected.PROFILE){const ps=storage();ps.setItem(storageKey,JSON.stringify({version:4,bags:{},profile:key}));const stable=createSelector(ps,rng());for(let i=0;i<10;i++)assert.equal(stable.createPage().sync([profile]).get('profile'),key);assert.equal(createSelector(ps,rng()).createPage().sync([profile]).get('profile'),key);seen.add(key);}
assert.deepEqual([...seen].sort(),Object.keys(assets).sort());
const profileSelections=new Set();for(let i=1;i<=80;i++)profileSelections.add(createSelector(storage(),()=>i/100).createPage().sync([profile]).get('profile'));assert.deepEqual([...profileSelections].sort(),[...expected.PROFILE].sort());
// A pending bag entry occupied by another scope must stay pending, without hiding a usable image.
const deferred=storage();deferred.setItem(storageKey,JSON.stringify({version:4,bags:{HEADER:{remaining:['sniff'],used:scopes.HEADER.filter(k=>k!=='sniff'),last:'rest'},CALM:{remaining:['sniff',...scopes.CALM.filter(k=>k!=='sniff')],used:[],last:null}}}));
const deferSelector=createSelector(deferred,rng()),deferRegions=[profile,region('rest'),region('list-header')];
const deferPage=deferSelector.createPage();deferPage.sync([profile,region('rest')]);
const deferResult=deferPage.sync(deferRegions);valid(deferRegions,deferResult);
assert.equal(deferResult.get('rest'),'sniff');assert.notEqual(deferResult.get('list-header'),'sniff');
assert.deepEqual(JSON.parse(deferred.getItem(storageKey)).bags.HEADER.remaining,['sniff']);
assert.equal(deferSelector.createPage().sync([region('list-header')]).get('list-header'),'sniff');
// Existing general regions yield to newly appearing errors, including fully occupied WAITING pool.
const collisionPage=createSelector(storage(),rng()).createPage();
const fullEmpty=[profile,...Array.from({length:scopes.EMPTY.length},(_,i)=>region('empty','body-'+i))];valid(fullEmpty,collisionPage.sync(fullEmpty));
const withError=[...fullEmpty,region('error')],collision=collisionPage.sync(withError);valid(withError,collision,false);
assert.ok(expected.WAITING.includes(collision.get('error')));
assert.equal([...collision.values()].filter(Boolean).length,scopes.EMPTY.length+1);
const collisionStable=collisionPage.sync(withError);assert.deepEqual(collisionStable,collision);
// Real exhaustion may omit; releasing a slot retries and restores the omitted region.
const crowded=createSelector(storage(),rng()).createPage(),crowd=[profile,...Array.from({length:6},(_,i)=>region('error','error-'+i))];
const crowdedResult=crowded.sync(crowd);valid(crowd,crowdedResult,false);assert.equal([...crowdedResult.values()].filter(Boolean).length,6);
const removeId=crowd.find(r=>r.role==='error'&&crowdedResult.get(r.id)).id;valid(crowd.filter(r=>r.id!==removeId),crowded.sync(crowd.filter(r=>r.id!==removeId)));
// Resource failures use only one replacement within the same pool, then preserve the empty slot.
for(const role of ['error','rest','list-header','success-create','profile']){
 const page=createSelector(storage(),rng()).createPage(),regions=role==='profile'?[profile]:[profile,region(role)],attempted=new Set();let result=page.sync(regions);
 while(result.get(role)){const key=result.get(role);assert.ok(!attempted.has(key));attempted.add(key);page.fail(role,key);result=page.sync(regions);}
 assert.equal(attempted.size,2);assert.deepEqual(page.sync(regions),result);
}
const bad=storage();bad.setItem('frizer.choco.global.v2',JSON.stringify({recent:['sniff']}));bad.setItem('frizer.choco.empty.v5','old');
bad.setItem(storageKey,JSON.stringify({version:4,bags:{HEADER:{remaining:['sniff','sniff'],used:[]},ERROR:{remaining:['happy-sit'],used:[]}}}));
valid(home('empty'),createSelector(bad,rng()).createPage().sync(home('empty')));
assert.equal(bad.getItem('frizer.choco.global.v2'),null);assert.equal(bad.getItem('frizer.choco.empty.v5'),null);
for(const blocked of [undefined,new Proxy({}, {get(){throw Error('disabled')}})])valid(home('warning'),createSelector(blocked,rng()).createPage().sync(home('warning')));
assert.ok(expected.PROFILE.includes(createSelector(storage()).createPage().sync([{id:'profile',role:'profile',fixed:'sniff'}]).get('profile')));
// Production DOM adapter: stable rerenders, recreated nodes, BFCache and bounded error retries.
const domStore=storage(),events={};let observer;
function img(id,role,fixed){let src='';return {dataset:{chocoRegion:id,chocoRole:role,chocoBase:'/assets/choco/',...(fixed?{chocoFixed:fixed}:{})},hidden:!fixed,parentElement:{getClientRects:()=>[{}],closest:()=>null},classList:{contains(){return false},toggle(){},remove(){}},get src(){return src},set src(v){src=v},removeAttribute:k=>{if(k==='src')src=''}};}
let images=[img('profile','profile','happy-closeup'),img('list-header','list-header')];const tasks=[];
const sandbox={URL,Map,Set,Math,console,location:{href:'http://localhost/inventory',pathname:'/inventory',search:''},sessionStorage:domStore,document:{body:{},querySelectorAll:()=>images},queueMicrotask:fn=>tasks.push(fn),MutationObserver:class{constructor(fn){observer=fn}observe(){}},addEventListener:(name,fn)=>events[name]=fn};
sandbox.window=sandbox;vm.createContext(sandbox);const flush=()=>{while(tasks.length)tasks.shift()()};
vm.runInContext(fs.readFileSync('src/main/resources/static/js/choco-selector.js','utf8'),sandbox);
const adapter=fs.readFileSync('src/main/resources/static/js/choco.js','utf8');vm.runInContext(adapter,sandbox);
const firstSrc=images[1].src,initial=domStore.getItem(storageKey);observer();observer();flush();vm.runInContext(adapter,sandbox);
assert.equal(images[1].src,firstSrc);assert.equal(domStore.getItem(storageKey),initial);
images[1]=img('list-header','list-header');observer();flush();assert.equal(images[1].src,firstSrc);
images.push(img('list-empty','empty'));observer();flush();assert.equal(images[1].src,firstSrc);assert.notEqual(images[2].src,firstSrc);
const headerAfter=JSON.parse(domStore.getItem(storageKey)).bags.HEADER;images.pop();observer();flush();images.push(img('list-empty','empty'));observer();flush();
assert.deepEqual(JSON.parse(domStore.getItem(storageKey)).bags.HEADER,headerAfter);
events.pageshow({persisted:true});flush();assert.notEqual(images[1].src,firstSrc);
images[1].onerror();flush();assert.ok(images[1].src);
images[1].onerror();flush();assert.equal(images[1].src,'');assert.equal(images[1].hidden,false,'failed slot must retain layout');
console.log('PASS: ADDED 15 + REUSED 5 = 20, missing 0; 36 live assets; original hashes; exact groups; 26-image headers; 2 session-stable profiles; 5 errors; independent persisted shuffle bags (12 cycles/scope); 360 home allocations; profile; error priority; deferred candidates; exhaustion/recovery; load failures; storage migration/failure; DOM stability and BFCache');
