const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function element(text = '') {
  return {textContent:text, dataset:{}, listeners:{}, attrs:{}, hidden:false,
    addEventListener(type,fn) {(this.listeners[type]??=[]).push(fn)},
    dispatchEvent(event) {(this.listeners[event.type]??[]).forEach(fn=>fn(event))},
    setAttribute(name,value) {this.attrs[name]=value}, focus() {document.activeElement=this}};
}
const document=element(), window=element(), picker=element(), select=element(), summary=element(), label=element();
const options=[element('음식을 골라줘'),element('긴 이름 '.repeat(25)),element('다른 음식')];
options.forEach((e,i)=>e.dataset.value=i===0?'':String(i));
select.value='1'; select.disabled=false;
picker.querySelector=s=>s==='summary'?summary:label;
picker.querySelectorAll=()=>options;
picker.contains=e=>[picker,summary,label,...options].includes(e);
document.getElementById=id=>id==='mergePicker'?picker:select;
vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/food-target-picker.js','utf8'),
  {document,window,Event:class {constructor(type){this.type=type}}});
assert.equal(select.hidden,true); assert.equal(picker.hidden,false);
assert.equal(label.textContent,options[1].textContent); assert.equal(options[1].attrs['aria-pressed'],'true');
let changes=0;select.addEventListener('change',()=>changes++);
options[2].dispatchEvent({type:'click'});
assert.equal(select.value,'2');assert.equal(changes,1);assert.equal(picker.open,false);assert.equal(document.activeElement,summary);
let prevented=0;
const key=key=>picker.dispatchEvent({type:'keydown',key,preventDefault(){prevented++}});
key('ArrowDown');assert.equal(document.activeElement,options[0]);assert.equal(picker.open,true);
key('End');assert.equal(document.activeElement,options[2]);
key('Home');assert.equal(document.activeElement,options[0]);
key('ArrowUp');assert.equal(document.activeElement,options[2]);
key('Escape');assert.equal(picker.open,false);assert.equal(document.activeElement,summary);
select.disabled=true; options[1].dispatchEvent({type:'click'});assert.equal(select.value,'2');assert.equal(changes,1);
summary.dispatchEvent({type:'click',preventDefault(){prevented++}});assert.equal(prevented,6);
select.disabled=false;picker.open=true;document.dispatchEvent({type:'click',target:{}});assert.equal(picker.open,false);
select.value='1';window.dispatchEvent({type:'pageshow'});assert.equal(label.textContent,options[1].textContent);
options[0].dispatchEvent({type:'click'});assert.equal(select.value,'');assert.equal(changes,2);
console.log('food-target-picker: full labels, selection, clear, keyboard, disabled guard, outside close and restore passed');
