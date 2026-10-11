const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function element(){return {value:'',hidden:false,disabled:false,textContent:'',children:[],dataset:{},listeners:{},addEventListener(k,f){this.listeners[k]=f},setAttribute(k,v){this[k]=v},replaceChildren(...c){this.children=c},append(c){this.children.push(c)}};}
function option(value,name){const o=element();o.dataset={value:String(value),name};o.selected=false;o.classList={toggle(c,v){o.selected=v}};return o;}
function setup(withPicker=false){
 const ids=Object.fromEntries(['itemMove','moveStatus','itemMovePreview','moveRetry','moveTarget','itemMoveSubmit','moveFields','moveName','moveCategory','moveFilter','moveFilterEmpty','existingMove','newMove'].map(id=>[id,element()]));
 const radio=element(), otherRadio=element(), button=element(), requests=[], window=element(), results={};
 radio.value='EXISTING';
 const categoryState={valid:false};
 if(withPicker) window.frizerCategoryPicker={switchContext(){},isValid(){return categoryState.valid},restore(){}};
 const optionsBox=element();
 const choices=[option(2,'두부'),option(3,'콩')];
 optionsBox.querySelectorAll=()=>choices;
 ids.moveTargetOptions=optionsBox;
 ids.itemMove.dataset={baseUrl:'/foods/1/move',items:'11,12'};
 ids.itemMove.querySelector=s=>s.includes('[name=mode]')?radio:(results[s]??=element());
 ids.itemMove.querySelectorAll=s=>s.includes('[name=mode]')?[radio,otherRadio]:[button];
 ids.itemMoveSubmit.querySelector=()=>button;
 const document={getElementById:id=>(ids[id]??=element()),createElement:()=>element()};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/item-move.js','utf8'),{document,window,URLSearchParams,fetch:url=>new Promise((resolve,reject)=>requests.push({url,resolve,reject}))});
 const pick=index=>ids.moveTargetOptions.listeners.click({target:{closest:()=>choices[index]}});
 return {ids,radio,button,requests,results,choices,pick,categoryState};
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const response=id=>({ok:true,json:async()=>({source:{foodName:'듀부'},targetMajorLabel:'콩·두부',targetMinorLabel:'두부',items:[{foodId:11},{foodId:12}],whole:false,endedCount:1,remainingCount:1,historyCount:1,requestId:'token'+id,command:{mode:'EXISTING',targetId:id,sourceId:1,sourceVersion:0,targetVersion:0,itemIds:[11,12],whole:false}})});
(async()=>{
 // 선택 응답 역전: 나중 선택의 결과만 반영한다.
 const e=setup(), {ids}=e;
 e.pick(0);e.pick(1);
 assert.equal(ids.moveTarget.value,'3');
 assert.ok(e.choices[1].selected);assert.ok(!e.choices[0].selected);
 e.requests[1].resolve(response(3));await tick();
 e.requests[0].resolve(response(2));await tick();
 assert.deepEqual(ids.moveFields.children.map(c=>[c.name,c.value]),[['targetVersion','0']]);
 assert.equal(e.results['[data-result="moveCount"]'].textContent,'2건');
 assert.equal(ids.itemMovePreview.hidden,false);
 // 통신 실패 후 재시도.
 e.pick(0);e.requests[2].reject(new Error('offline'));await tick();
 assert.equal(ids.moveRetry.hidden,false);assert.equal(ids.itemMovePreview.hidden,true);
 ids.moveRetry.listeners.click();e.requests[3].resolve(response(2));await tick();
 assert.equal(ids.itemMovePreview.hidden,false);
 // 제출 잠금과 중복 제출 차단.
 let blocked=0;const submit={preventDefault(){blocked++}};
 ids.itemMoveSubmit.listeners.submit(submit);
 assert.equal(e.button.textContent,'병합하는 중…');assert.equal(e.button.disabled,true);
 ids.itemMoveSubmit.listeners.submit(submit);assert.equal(blocked,1);
 assert.equal(e.requests.length,4);
 assert.ok(e.requests.every(r=>r.url.includes('/preview?') && r.url.includes('items=11') && r.url.includes('items=12')));

 // 클라이언트 필터링: 이름 부분 일치, 빈 결과 안내, 선택 유지.
 const f=setup();
 f.pick(0);f.requests[0].resolve(response(2));await tick();
 f.ids.moveFilter.value='두';f.ids.moveFilter.listeners.input();
 assert.equal(f.choices[0].hidden,false);assert.equal(f.choices[1].hidden,true);
 assert.equal(f.ids.moveFilterEmpty.hidden,true);
 assert.ok(f.choices[0].selected);
 f.ids.moveFilter.value='없는이름';f.ids.moveFilter.listeners.input();
 assert.ok(f.choices.every(c=>c.hidden));assert.equal(f.ids.moveFilterEmpty.hidden,false);
 f.ids.moveFilter.value='';f.ids.moveFilter.listeners.input();
 assert.ok(f.choices.every(c=>!c.hidden));assert.equal(f.ids.moveFilterEmpty.hidden,true);
 assert.equal(f.ids.moveTarget.value,'2');

 // 모드 전환 경합: 이전 EXISTING 응답은 무시하고 NEW 결과만 남긴다.
 const change=setup();
 change.pick(0);
 change.radio.value='NEW';change.ids.moveName.value='콩';change.radio.listeners.change();
 change.requests[1].resolve(response(4));await tick();
 change.requests[0].resolve(response(2));await tick();
 assert.equal(change.ids.moveFields.children.find(c=>c.name==='targetVersion').value,'0');
 change.ids.moveName.value='새 이름';change.ids.moveName.listeners.input();
 assert.equal(change.ids.itemMovePreview.hidden,true);
 assert.ok(change.requests[2].url.includes('newName='));
 change.requests[2].resolve(response(4));await tick();
 assert.equal(change.ids.itemMovePreview.hidden,false);
 // 분류 변경은 별도 확인 버튼 없이 다시 조회하며 불완전한 선택은 이전 결과를 무효화한다.
 const category=setup(true);
 category.radio.value='NEW';category.ids.moveName.value='새 음식';
 category.ids.categoryMajorCode.value='soy';category.ids.categoryMajorCode.listeners.change();
 assert.equal(category.requests.length,0);assert.equal(category.ids.itemMovePreview.hidden,true);
 category.categoryState.valid=true;category.ids.categoryMinorCode.value='soy_tofu';category.ids.categoryMinorCode.listeners.change();
 assert.equal(category.requests.length,1);assert.ok(category.requests[0].url.includes('categoryMinorCode=soy_tofu'));
 category.categoryState.valid=false;category.ids.categoryMajorCode.listeners.change();
 category.requests[0].resolve(response(9));await tick();assert.equal(category.ids.itemMovePreview.hidden,true);
 category.categoryState.valid=true;category.ids.categoryMinorCode.listeners.change();
 category.requests[1].resolve(response(9));await tick();assert.equal(category.ids.itemMovePreview.hidden,false);
 assert.equal(category.results['[data-result="targetMajorLabel"]'].textContent,'콩·두부');
 assert.equal(category.results['[data-result="targetMinorLabel"]'].textContent,'두부');
 console.log('item-move: preloaded list selection, stale previews, retry, filtering, mode race and duplicate submission passed');
})().catch(error=>{console.error(error);process.exitCode=1});
