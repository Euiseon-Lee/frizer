const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

function picker({mode='new',major='',minor='',error=false,inherited=false,refresh=false}={}) {
  const catalog=[{code:'soy',label:'콩·두부',requiresMinor:true,minors:[{code:'soy_tofu',label:'두부',example:'부침두부·찌개두부'},
    {code:'soy_soft_tofu',label:'순두부·연두부',example:'순두부·연두부'}]},
    {code:'grain',label:'곡물류',requiresMinor:true,minors:[{code:'grain_bread',label:'빵',example:'식빵'}]},
    {code:'kimchi',label:'김치',example:'배추김치',requiresMinor:false,minors:[]}];
  function element(){return {value:'',textContent:'',hidden:false,disabled:false,required:false,dataset:{},listeners:{},attrs:{},options:[],
    classList:{add(){},remove(){}},setAttribute(k,v){this.attrs[k]=v},focus(){this.focused=true},
    addEventListener(k,f){(this.listeners[k]??=[]).push(f)},emit(k,e={}){for(const f of this.listeners[k]||[])f(e)},
    replaceChildren(){this.options=[]},add(o){this.options.push(o)}}}
  const ids=Object.fromEntries(['categoryPicker','foodCategoryCatalog','categoryMajorCode','categoryMinorCode','categoryPanel',
    'categoryToggle','categorySummary','categoryExample','categoryPending','sharedCategoryHelp','categoryLegacy','categoryInherited',
    'categoryMajorError','categoryMinorError','categoryMinorField','categoryConfirm','categoryCancel','categoryActions',
    'masterId','registrationSubmit'].map(id=>[id,element()]));
  ids.categoryMajorCode.value=major;ids.categoryMinorCode.value=minor;
  ids.categoryMajorError.hidden=!error;ids.categoryMinorError.hidden=true;
  ids.categoryPicker.dataset={refresh:String(refresh),inherited:String(inherited)};
  ids.foodCategoryCatalog.textContent=JSON.stringify(catalog);ids.masterId.value='1';
  const form=element(),window=element(),refreshButton=element();ids.categoryPicker.closest=()=>form;ids.categoryPicker.querySelector=()=>refreshButton;
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/food-category.js','utf8'),{
    document:{getElementById:id=>ids[id],querySelector:()=>mode==='edit'?null:{value:mode}},window,
    Option:function(text,value){this.text=text;this.value=value}});
  return {ids,window,change(id,value){ids[id].value=value;ids[id].emit('change')},click(id){ids[id].emit('click')},
    submit(){const event={defaultPrevented:false,preventDefault(){this.defaultPrevented=true}};form.emit('submit',event);return event},
    context(mode,selected,reset=false){window.frizerCategoryPicker.switchContext(mode,selected,reset)}};
}
test('category: missing selection opens and blocks both submit-button and Enter paths',()=>{
  const p=picker();assert.equal(p.ids.categoryPanel.hidden,true);assert.equal(p.submit().defaultPrevented,true);
  assert.equal(p.ids.categoryPanel.hidden,false);assert.equal(p.ids.categoryMajorCode.focused,true);
  p.click('categoryCancel');assert.equal(p.submit().defaultPrevented,true);
});
test('category: draft is not a confirmed selection and cancel restores both codes',()=>{
  const p=picker({major:'soy',minor:'soy_tofu'});p.click('categoryToggle');p.change('categoryMajorCode','grain');
  assert.equal(p.ids.categoryMinorCode.value,'');assert.equal(p.submit().defaultPrevented,true);
  assert.match(p.ids.categoryPending.textContent,/완료하거나 취소/);p.click('categoryToggle');assert.equal(p.ids.categoryPanel.hidden,false);
  p.click('categoryCancel');assert.equal(p.ids.categoryMajorCode.value,'soy');assert.equal(p.ids.categoryMinorCode.value,'soy_tofu');
  assert.equal(p.ids.categorySummary.textContent,'콩·두부 › 두부');assert.equal(p.submit().defaultPrevented,false);
});
test('category: confirmation requires minor, leaf hides and disables minor',()=>{
  const p=picker();p.change('categoryMajorCode','soy');p.click('categoryConfirm');assert.equal(p.ids.categoryMinorError.hidden,false);
  p.change('categoryMinorCode','soy_tofu');p.click('categoryConfirm');assert.equal(p.ids.categorySummary.textContent,'콩·두부 › 두부');
  assert.equal(p.ids.categoryExample.textContent,'예) 두부, 부침두부, 찌개두부 등');
  p.change('categoryMinorCode','soy_soft_tofu');assert.equal(p.ids.categoryExample.textContent,'예) 순두부, 연두부 등');
  p.click('categoryToggle');p.change('categoryMajorCode','kimchi');p.click('categoryConfirm');
  assert.equal(p.ids.categoryMinorField.hidden,true);assert.equal(p.ids.categoryMinorCode.disabled,true);assert.equal(p.ids.categoryMinorCode.required,false);
  assert.equal(p.ids.categorySummary.textContent,'김치');assert.equal(p.submit().defaultPrevented,false);
});
test('category: new draft and open state survive existing and bulk modes; target reselection clears its draft',()=>{
  const p=picker({major:'soy',minor:'soy_tofu'});p.click('categoryToggle');p.change('categoryMajorCode','grain');
  const target={dataset:{masterId:'2',category:''}};p.context('existing',target,true);assert.equal(p.ids.categoryMajorCode.value,'');
  p.change('categoryMajorCode','kimchi');p.click('categoryConfirm');p.context('bulk');assert.equal(p.ids.categoryMajorCode.disabled,true);
  p.context('new');assert.equal(p.ids.categoryMajorCode.value,'grain');assert.equal(p.ids.categoryPanel.hidden,false);
  p.click('categoryCancel');assert.equal(p.ids.categorySummary.textContent,'콩·두부 › 두부');
  p.context('existing',target,true);assert.equal(p.ids.categorySummary.textContent,'분류 선택하기');
});
test('category: inherited selection is read-only and never posts unrelated new codes',()=>{
  const p=picker({major:'soy',minor:'soy_tofu'});
  p.context('existing',{dataset:{masterId:'2',major:'kimchi',category:'김치'}},true);
  assert.equal(p.ids.categoryInherited.textContent,'김치');assert.equal(p.ids.categoryMajorCode.disabled,true);
  assert.equal(p.ids.categoryMinorCode.disabled,true);assert.equal(p.ids.categoryToggle.hidden,true);assert.equal(p.submit().defaultPrevented,false);
});
test('category: errors and no-JS refresh redisplay expanded; edit locks only successful submission',()=>{
  assert.equal(picker({error:true}).ids.categoryPanel.hidden,false);assert.equal(picker({refresh:true}).ids.categoryPanel.hidden,false);
  const p=picker({mode:'edit'});assert.equal(p.submit().defaultPrevented,true);assert.equal(p.ids.registrationSubmit.disabled,false);
  p.change('categoryMajorCode','kimchi');p.click('categoryConfirm');assert.equal(p.submit().defaultPrevented,false);
  assert.equal(p.ids.registrationSubmit.disabled,true);assert.equal(p.submit().defaultPrevented,true);
});
test('category: restored values update summary, BFCache forces fresh server version',()=>{
  const p=picker();p.ids.categoryMajorCode.value='soy';p.ids.categoryMinorCode.value='soy_tofu';p.window.emit('pageshow',{persisted:false});
  assert.equal(p.ids.categorySummary.textContent,'콩·두부 › 두부');let reload=false;
  p.window.location={reload(){reload=true}};p.window.emit('pageshow',{persisted:true});assert.equal(reload,true);
});
