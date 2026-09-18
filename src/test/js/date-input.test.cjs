const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/js/date-input.js','utf8');
function dateInput(value=''){const classes=new Set();return {value,classes,classList:{toggle(name,on){on?classes.add(name):classes.delete(name)}}};}

// 초기 로드에서 빈 입력만 date-empty로 표시한다.
const empty=dateInput(),filled=dateInput('2026-09-18');
const listeners={};
const document={querySelectorAll:()=>[empty,filled],addEventListener:(type,fn)=>{listeners[type]=fn}};
const window={addEventListener:(type,fn)=>{listeners[type]=fn}};
vm.runInNewContext(source,{document,window});
assert.equal(empty.classes.has('date-empty'),true);
assert.equal(filled.classes.has('date-empty'),false);

// 다른 스크립트가 값을 채우고 change만 발화해도(오늘 넣었어 체크) 위임 리스너가 동기화한다.
empty.value='2026-09-01';listeners.change();
assert.equal(empty.classes.has('date-empty'),false);

// 값을 지우면 input 발화로 다시 안내가 나온다. BFCache 복귀(pageshow)도 같은 동기화를 탄다.
filled.value='';listeners.input();
assert.equal(filled.classes.has('date-empty'),true);
listeners.pageshow();
assert.equal(empty.classes.has('date-empty'),false);

console.log('date-input: empty-hint class sync on load, delegated events and pageshow passed');
