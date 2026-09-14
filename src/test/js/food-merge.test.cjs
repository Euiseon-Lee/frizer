const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/food-merge.js', 'utf8');
function element() {return {hidden:false,disabled:false,value:'',textContent:'',dataset:{},listeners:{},children:[],addEventListener(k,f){this.listeners[k]=f},setAttribute(k,v){this[k]=v},replaceChildren(...c){this.children=c}};}
function setup() {
 const root=element(), select=element(), slot=element(), status=element(), retry=element(), requests=[],window=element();
 root.dataset={previewUrl:'/foods/1/merge',sourceId:'1'};
 root.querySelector=s=>({'#targetId':select,'#mergePreviewSlot':slot,'#mergeStatus':status,'#mergeRetry':retry}[s]);
 const document={getElementById:()=>root,importNode:n=>n};
 class DOMParser {parseFromString(html){const data=JSON.parse(html);return {querySelector:()=>data.invalid?null:{dataset:{sourceId:data.source||'1'},querySelector:()=>({value:data.target})}}}}
 vm.runInNewContext(source,{document,window,DOMParser,URL,AbortController,location:{origin:'http://localhost'},fetch:(url,options)=>new Promise((resolve,reject)=>requests.push({url,options,resolve,reject}))});
 return {root,select,slot,status,retry,requests,window};
}
const tick=()=>new Promise(r=>setImmediate(r));
const ok=target=>({ok:true,redirected:false,text:async()=>JSON.stringify({target})});
(async()=>{
 const e=setup();
 e.select.value='2';e.select.listeners.change();assert.equal(e.slot.children.length,0);
 assert.match(e.status.textContent,/확인하고/);
 e.select.value='3';e.select.listeners.change();assert.equal(e.requests[0].options.signal.aborted,true);
 e.requests[1].resolve(ok('3'));await tick();assert.equal(e.slot.children[0].querySelector().value,'3');
 e.requests[0].resolve(ok('2'));await tick();assert.equal(e.slot.children[0].querySelector().value,'3');
 e.select.value='';e.select.listeners.change();assert.equal(e.slot.children.length,0);
 e.select.value='2';e.select.listeners.change();e.requests[2].reject(new Error('offline'));await tick();
 assert.equal(e.retry.hidden,false);assert.equal(e.slot.children.length,0);
 e.retry.listeners.click();e.requests[3].resolve(ok('3'));await tick();assert.equal(e.slot.children.length,0);assert.equal(e.retry.hidden,false);
 e.retry.listeners.click();e.requests[4].resolve(ok('2'));await tick();assert.equal(e.slot.children.length,1);
 const button=element(),form={matches:()=>true,querySelector:s=>s==='button[type="submit"]'?button:{value:'2'}};
 let prevented=0;const event={target:form,preventDefault(){prevented++}};
 e.root.listeners.submit(event);assert.equal(e.select.disabled,true);assert.equal(button.textContent,'이동하는 중…');assert.equal(prevented,0);
 e.root.listeners.submit(event);assert.equal(prevented,1);assert.equal(e.requests.length,5,'selection only makes preview requests');
 e.window.listeners.pageshow({persisted:true});assert.equal(e.slot.children.length,0);assert.equal(e.select.disabled,false);
 for (const response of [
   {ok:false,redirected:false}, {ok:true,redirected:true},
   {ok:true,redirected:false,text:async()=>JSON.stringify({invalid:true})},
   {ok:true,redirected:false,text:async()=>JSON.stringify({target:'2',source:'999'})},
   {ok:true,redirected:false,text:async()=>{throw new Error('truncated response')}}
 ]) {
   const failure=setup();failure.select.value='2';failure.select.listeners.change();
   failure.requests[0].resolve(response);await tick();
   assert.equal(failure.slot.children.length,0);assert.equal(failure.retry.hidden,false);
   assert.equal(failure.slot['aria-busy'],'false');
 }
 const cleared=setup();cleared.select.value='2';cleared.select.listeners.change();
 cleared.select.value='';cleared.select.listeners.change();cleared.requests[0].resolve(ok('2'));await tick();
 assert.equal(cleared.slot.children.length,0);assert.equal(cleared.status.textContent,'');
 const mismatch=setup();mismatch.select.value='3';let blocked=0;
 mismatch.root.listeners.submit({target:form,preventDefault(){blocked++}});
 assert.equal(blocked,1);assert.equal(mismatch.select.disabled,false);
 const restored=setup();restored.select.value='2';restored.window.listeners.pageshow({persisted:true});
 assert.equal(restored.requests.length,1);restored.requests[0].resolve(ok('2'));await tick();
 assert.equal(restored.slot.children.length,1);
 console.log('food-merge: stale response, clear, failure/retry, mismatch, duplicate submission and history restore passed');
})().catch(error=>{console.error(error);process.exitCode=1});
