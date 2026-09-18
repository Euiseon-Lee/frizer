const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync('src/main/resources/static/js/busy-buttons.js','utf8');
function button(tag='BUTTON'){const classes=new Set();
 return {tagName:tag,disabled:false,dataset:{},classes,classList:{add(c){classes.add(c)},remove(c){classes.delete(c)}},
  target:'',attrs:{},hasAttribute(n){return n in this.attrs},getAttribute(n){return this.attrs[n]??null}};}
function form(method,submitButton){return {attrs:{method},getAttribute(n){return this.attrs[n]??null},querySelector:()=>submitButton??null};}
function env(){
 const locked=[];
 const doc={listeners:{},addEventListener(n,f){this.listeners[n]=f},querySelectorAll:()=>locked.slice()};
 const win={listeners:{},addEventListener(n,f){this.listeners[n]=f}};
 vm.runInNewContext(source,{document:doc,window:win});
 return {doc,win,locked,
  submit(f,submitter){const e={target:f,submitter,defaultPrevented:false,preventDefault(){e.defaultPrevented=true}};doc.listeners.submit(e);return e;},
  click(target){const e={target,defaultPrevented:false,preventDefault(){e.defaultPrevented=true}};doc.listeners.click(e);return e;}};}

// POST 제출은 제출 버튼을 잠그고 스피너를 붙이며, 반복 제출을 막는다.
const a=env();const b=button();const f=form('post',b);
let e=a.submit(f,b);
assert.equal(e.defaultPrevented,false);assert.equal(b.disabled,true);
assert.ok(b.classes.has('busy-indicator'));assert.equal(b.dataset.busyLock,'true');
e=a.submit(f,b);
assert.equal(e.defaultPrevented,true);

// GET 폼과 이미 막힌 제출은 건드리지 않는다.
const c=env();const gb=button();
c.submit(form('get',gb),gb);assert.equal(gb.disabled,false);
const pb=button();const pf=form('post',pb);
const blocked={target:pf,submitter:pb,defaultPrevented:true,preventDefault(){}};
c.doc.listeners.submit(blocked);assert.equal(pb.disabled,false);

// submitter가 없으면 폼의 제출 버튼을 찾는다.
const d=env();const fb=button();
d.submit(form('post',fb),undefined);assert.equal(fb.disabled,true);

// 이동 버튼·복귀 링크 클릭은 스피너만 붙이고 이동은 막지 않는다. 다운로드 링크는 제외.
const g=env();const link=button('A');
e=g.click({closest:()=>link});
assert.equal(e.defaultPrevented,false);assert.ok(link.classes.has('busy-indicator'));
const dl=button('A');dl.attrs.download='';
g.click({closest:()=>dl});assert.ok(!dl.classes.has('busy-indicator'));
g.click({closest:()=>null});

// pageshow(BFCache 복귀 포함)는 잠갔던 버튼을 전부 되살린다.
const h=env();const hb=button();
h.submit(form('post',hb),hb);h.locked.push(hb);
h.win.listeners.pageshow();
assert.equal(hb.disabled,false);assert.ok(!hb.classes.has('busy-indicator'));assert.equal('busyLock' in hb.dataset,false);

console.log('busy-buttons: post submit lock, repeat block, nav spinner and pageshow release passed');
