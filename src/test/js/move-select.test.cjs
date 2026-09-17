const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/js/move-select.js','utf8');
function element(){return {disabled:false,listeners:{},addEventListener(n,f){(this.listeners[n]??=[]).push(f)},fire(n,e){(this.listeners[n]||[]).forEach(f=>f(e))}};}

// 보관 중 탭: 병합·삭제 버튼이 함께 선택 여부를 따라간다.
const move=element(),del=element(),form=element();
let checked=null;
form.querySelector=()=>checked;
const windowListeners={};
const window={addEventListener:(n,f)=>{(windowListeners[n]??=[]).push(f)}};
const document={getElementById:id=>id==='moveSelectForm'?form:id==='moveSelected'?move:id==='deleteSelected'?del:null};
vm.runInNewContext(source,{document,window});
assert.equal(move.disabled,true);assert.equal(del.disabled,true);
checked={};form.fire('change',{target:{name:'items'}});
assert.equal(move.disabled,false);assert.equal(del.disabled,false);
checked=null;form.fire('change',{target:{name:'items'}});
assert.equal(move.disabled,true);assert.equal(del.disabled,true);
// BFCache 복귀 시 현재 체크 상태로 다시 맞춘다.
checked={};windowListeners.pageshow.forEach(f=>f());
assert.equal(move.disabled,false);assert.equal(del.disabled,false);
// items가 아닌 입력 변경은 무시한다.
checked=null;form.fire('change',{target:{name:'warning'}});
assert.equal(move.disabled,false);

// 버튼이 하나만 있는 화면에서도 동작한다.
const onlyMove=element(),soloForm=element();
soloForm.querySelector=()=>null;
const soloDocument={getElementById:id=>id==='moveSelectForm'?soloForm:id==='moveSelected'?onlyMove:null};
vm.runInNewContext(source,{document:soloDocument,window:{addEventListener(){}}});
assert.equal(onlyMove.disabled,true);

// 폼이 없는 화면에서는 아무것도 하지 않는다.
vm.runInNewContext(source,{document:{getElementById:()=>null},window:{addEventListener(){}}});

console.log('move-select: merge/delete buttons follow selection, pageshow resync and missing-node guards passed');
