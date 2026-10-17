const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

function picker({mode='new',major='',minor='',error=false,inherited=false}={}) {
  const catalog=[{code:'soy',label:'콩·두부',requiresMinor:true,minors:[{code:'soy_tofu',label:'두부',example:'두부·부침두부·찌개두부'},
    {code:'soy_soft_tofu',label:'순두부·연두부',example:'순두부·연두부'}]},
    {code:'grain',label:'곡물류',requiresMinor:true,minors:[{code:'grain_bread',label:'빵',example:'식빵'}]},
    {code:'vegetable',label:'채소',requiresMinor:true,minors:[{code:'vegetable_root',label:'뿌리·덩이채소',example:'무·당근·감자·고구마'}]},
    {code:'fruit',label:'과일',requiresMinor:true,minors:[{code:'fruit_fresh',label:'일반 과일',example:'사과·바나나·귤·딸기'}]},
    {code:'kimchi',label:'김치',example:'배추김치',requiresMinor:false,minors:[]}];
  function element(){return {value:'',textContent:'',hidden:false,disabled:false,required:false,dataset:{},listeners:{},attrs:{},options:[],
    classList:{add(){},remove(){}},setAttribute(k,v){this.attrs[k]=v},focus(){this.focused=true},
    addEventListener(k,f){(this.listeners[k]??=[]).push(f)},emit(k,e={}){for(const f of this.listeners[k]||[])f(e)},
    replaceChildren(){this.options=[]},add(o){this.options.push(o)}}}
  const ids=Object.fromEntries(['categoryPicker','foodCategoryCatalog','categoryMajorCode','categoryMinorCode','categoryPanel',
    'categoryExample','sharedCategoryHelp','categoryInherited','categoryInheritedHeading',
    'categoryMajorError','categoryMinorError','categoryMinorField',
    'masterId','registrationSubmit'].map(id=>[id,element()]));
  ids.categoryMajorCode.value=major;ids.categoryMinorCode.value=minor;
  ids.categoryMajorError.hidden=!error;ids.categoryMinorError.hidden=true;
  ids.categoryPicker.dataset={inherited:String(inherited),hideInherited:'true'};
  ids.foodCategoryCatalog.textContent=JSON.stringify(catalog);ids.masterId.value='1';
  const form=element(),window=element(),refreshButton=element();ids.categoryPicker.closest=()=>form;ids.categoryPicker.querySelector=()=>refreshButton;
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/food-category.js','utf8'),{
    document:{getElementById:id=>ids[id],querySelector:()=>mode==='edit'?null:{value:mode}},window,
    Option:function(text,value){this.text=text;this.value=value}});
  return {ids,window,change(id,value){ids[id].value=value;ids[id].emit('change')},click(id){ids[id].emit('click')},
    submit(){const event={defaultPrevented:false,preventDefault(){this.defaultPrevented=true}};form.emit('submit',event);return event},
    context(mode,selected,reset=false){window.frizerCategoryPicker.switchContext(mode,selected,reset)}};
}
test('category: selection is always visible and missing codes block submission',()=>{
  const p=picker();assert.equal(p.ids.categoryPanel.hidden,false);assert.equal(p.submit().defaultPrevented,true);
  assert.equal(p.ids.categoryMajorCode.focused,true);
});
test('category: changing parent clears child and a valid choice submits directly',()=>{
  const p=picker({major:'soy',minor:'soy_tofu'});p.change('categoryMajorCode','grain');
  assert.equal(p.ids.categoryMinorCode.value,'');assert.equal(p.submit().defaultPrevented,true);
  p.change('categoryMinorCode','grain_bread');assert.equal(p.submit().defaultPrevented,false);
  assert.equal(p.window.frizerCategoryPicker.isValid(),true);
});
test('category: child selection updates examples and a leaf hides its child input',()=>{
  const p=picker();p.change('categoryMajorCode','soy');assert.equal(p.submit().defaultPrevented,true);
  p.change('categoryMinorCode','soy_tofu');assert.equal(p.ids.categoryExample.textContent,'예) 두부, 부침두부, 찌개두부 등');
  p.change('categoryMinorCode','soy_soft_tofu');assert.equal(p.ids.categoryExample.textContent,'예) 순두부, 연두부 등');
  p.change('categoryMajorCode','kimchi');assert.equal(p.ids.categoryMinorField.hidden,true);
  assert.equal(p.ids.categoryMinorCode.disabled,true);assert.equal(p.ids.categoryMinorCode.required,false);
  assert.equal(p.submit().defaultPrevented,false);
});
test('category: examples contain explicit items without prepending category labels',()=>{
  const p=picker({major:'vegetable',minor:'vegetable_root'});
  assert.equal(p.ids.categoryExample.textContent,'예) 무, 당근, 감자, 고구마 등');
  p.change('categoryMajorCode','fruit');p.change('categoryMinorCode','fruit_fresh');
  assert.equal(p.ids.categoryExample.textContent,'예) 사과, 바나나, 귤, 딸기 등');
});

test('category: new input survives existing and bulk modes; reselection clears target input',()=>{
  const p=picker({major:'soy',minor:'soy_tofu'});p.change('categoryMajorCode','grain');
  const target={dataset:{masterId:'2',category:''}};p.context('existing',target,true);
  assert.equal(p.ids.categoryMajorCode.value,'');p.change('categoryMajorCode','kimchi');
  p.context('bulk');assert.equal(p.ids.categoryMajorCode.disabled,true);
  p.context('new');assert.equal(p.ids.categoryMajorCode.value,'grain');assert.equal(p.ids.categoryPanel.hidden,false);
  p.context('existing',target,true);assert.equal(p.ids.categoryMajorCode.value,'');
});
test('category: inherited selection remains read-only and never posts unrelated codes',()=>{
  const p=picker({major:'soy',minor:'soy_tofu'});
  p.context('existing',{dataset:{masterId:'2',major:'kimchi',category:'김치'}},true);
  assert.equal(p.ids.categoryInherited.textContent,'김치');assert.equal(p.ids.categoryMajorCode.disabled,true);
  assert.equal(p.ids.categoryInheritedHeading.hidden,false);
  assert.equal(p.ids.categoryPicker.hidden,true);
  assert.equal(p.ids.sharedCategoryHelp.textContent,'기존 음식의 분류를 그대로 사용할 거야.');
  assert.equal(p.ids.categoryMinorCode.disabled,true);assert.equal(p.ids.categoryPanel.hidden,true);
  assert.equal(p.submit().defaultPrevented,false);
  p.context('new');assert.equal(p.ids.categoryInheritedHeading.hidden,true);
  assert.equal(p.ids.categoryPicker.hidden,false);
});
test('category: errors stay visible; edit locks only valid submission',()=>{
  assert.equal(picker({error:true}).ids.categoryPanel.hidden,false);
  const p=picker({mode:'edit'});assert.equal(p.submit().defaultPrevented,true);assert.equal(p.ids.registrationSubmit.disabled,false);
  p.change('categoryMajorCode','kimchi');assert.equal(p.submit().defaultPrevented,false);
  assert.equal(p.ids.registrationSubmit.disabled,true);assert.equal(p.submit().defaultPrevented,true);
});
test('category: browser-restored values are validated and BFCache reloads server state',()=>{
  const p=picker();p.ids.categoryMajorCode.value='soy';p.ids.categoryMinorCode.value='soy_tofu';p.window.emit('pageshow',{persisted:false});
  assert.equal(p.window.frizerCategoryPicker.isValid(),true);let reload=false;
  p.window.location={reload(){reload=true}};p.window.emit('pageshow',{persisted:true});assert.equal(reload,true);
});
