const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
function element(value){return {value,hidden:false,disabled:false,checked:false,listeners:{},addEventListener(n,f){(this.listeners[n]??=[]).push(f)},fire(n){(this.listeners[n]||[]).forEach(f=>f())}};}
const fields=element(),frozenAt=element(''),freezeToday=element('true'),freezeType=element('HOME_FROZEN');
frozenAt.max='2026-09-17';
const radios=[element('ROOM'),element('FRIDGE'),element('FREEZER')];
radios[1].checked=true;
fields.querySelectorAll=()=>[freezeType,frozenAt,freezeToday];
const document={
 getElementById:id=>id==='freezerFields'?fields:id==='frozenAt'?frozenAt:null,
 querySelector:s=>s.includes(':checked')?radios.find(r=>r.checked)??null:freezeToday,
 querySelectorAll:()=>radios,
};
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/food-split.js','utf8'),{document});
assert.equal(fields.hidden,true);
assert.ok([freezeType,frozenAt,freezeToday].every(i=>i.disabled));
radios[1].checked=false;radios[2].checked=true;radios[2].fire('change');
assert.equal(fields.hidden,false);
assert.ok([freezeType,frozenAt,freezeToday].every(i=>!i.disabled));
freezeToday.checked=true;freezeToday.fire('change');
assert.equal(frozenAt.value,'2026-09-17');
frozenAt.fire('input');
assert.equal(freezeToday.checked,false);
radios[2].checked=false;radios[0].checked=true;radios[0].fire('change');
assert.equal(fields.hidden,true);
console.log('food-split: freezer fields toggle, disable and today-checkbox sync passed');
