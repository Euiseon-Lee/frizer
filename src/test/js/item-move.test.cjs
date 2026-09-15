const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function element(){return {value:'',hidden:false,disabled:false,textContent:'',children:[],dataset:{},listeners:{},dispatchEvent(e){this.listeners[e.type]?.(e)},addEventListener(k,f){this.listeners[k]=f},setAttribute(k,v){this[k]=v},replaceChildren(...c){this.children=c},append(c){this.children.push(c)},add(c){this.children.push(c)}};}
function setup(){
 const ids=Object.fromEntries(['itemMove','moveStatus','itemMovePreview','moveRetry','moveTarget','itemMoveSubmit','moveFields','moveName','moveCategory','moveQuery','moveResults','moveSearch','existingMove','newMove','emptySourceNote'].map(id=>[id,element()]));
 const radio=element(), otherRadio=element(),button=element(),requests=[],window=element(),results={};radio.value='EXISTING';
 ids.itemMove.dataset={baseUrl:'/inventory/1/move'};
 ids.itemMove.querySelector=s=>s.includes('moveMode')?radio:(results[s]??=element());
 ids.itemMove.querySelectorAll=s=>s.includes('moveMode')?[radio,otherRadio]:[radio,otherRadio,ids.moveTarget,button];
 ids.itemMoveSubmit.querySelector=()=>button;
 const document={getElementById:id=>(ids[id]??=element()),createElement:()=>element()};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/item-move.js','utf8'),{document,window,Event:class {constructor(type){this.type=type}},URLSearchParams,setTimeout,clearTimeout,Option:function(text,value){this.text=text;this.value=value},fetch:url=>new Promise((resolve,reject)=>requests.push({url,resolve,reject}))});
 return {ids,radio,button,requests,results};
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const response=id=>({ok:true,json:async()=>({source:{foodName:'듀부'},item:{quantityAmount:2,quantityUnit:'개'},targetName:'두부'+id,targetCategory:null,historyCount:1,remainingCount:1,requestId:'token'+id,command:{mode:'EXISTING',targetId:id,sourceId:1,sourceVersion:0,targetVersion:0}})});
(async()=>{
 const e=setup(), {ids}=e;
 ids.moveTarget.value='2';ids.moveTarget.listeners.change();
 ids.moveTarget.value='3';ids.moveTarget.listeners.change();
 e.requests[1].resolve(response(3));await tick();
 e.requests[0].resolve(response(2));await tick();
 assert.equal(ids.moveFields.children.find(c=>c.name==='targetId').value,'3'); assert.equal(e.results['[data-result="sourceCount"]'].textContent,'2건');
 assert.equal(ids.itemMovePreview.hidden,false);
 ids.moveTarget.value='';ids.moveTarget.listeners.change();assert.equal(ids.itemMovePreview.hidden,true);assert.equal(ids.moveFields.children.length,0);
 let blocked=0;const submit={preventDefault(){blocked++}};ids.itemMoveSubmit.listeners.submit(submit);assert.equal(blocked,1);
 ids.moveTarget.value='2';ids.moveTarget.listeners.change();e.requests[2].reject(new Error('offline'));await tick();assert.equal(ids.moveRetry.hidden,false);
 ids.moveRetry.listeners.click();e.requests[3].resolve(response(2));await tick();assert.equal(ids.itemMovePreview.hidden,false);
 ids.itemMoveSubmit.listeners.submit(submit);assert.equal(e.button.textContent,'이동하는 중…');assert.equal(ids.moveTarget.disabled,true);
 ids.itemMoveSubmit.listeners.submit(submit);assert.equal(blocked,2);assert.equal(e.requests.length,4);
 assert.ok(e.requests.every(r=>r.url.includes('/preview?')));
 const search=setup();search.ids.moveQuery.value='두부';search.ids.moveSearch.listeners.submit({preventDefault(){}});
 search.ids.moveQuery.value='콩';search.ids.moveQuery.listeners.input();
 search.requests[0].resolve({ok:true,json:async()=>[{foodName:'두부',masterId:2,itemCount:1}]});await tick();
 assert.equal(search.ids.moveResults.hidden,true);assert.equal(search.ids.moveTarget.children.length,1);
 const change=setup();change.ids.moveTarget.value='2';change.ids.moveTarget.listeners.change();
 change.radio.value='NEW';change.ids.moveName.value='콩';change.radio.listeners.change();
 change.requests[1].resolve(response(4));await tick();change.requests[0].resolve(response(2));await tick();
 assert.equal(change.ids.moveFields.children.find(c=>c.name==='targetId').value,'4');
 change.ids.moveName.value='';change.ids.moveName.listeners.input();assert.equal(change.ids.itemMovePreview.hidden,true);
 console.log('item-move: stale previews/search, clear, retry, mode change, input invalidation and duplicate submission passed');
})().catch(error=>{console.error(error);process.exitCode=1});
