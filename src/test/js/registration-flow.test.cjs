const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function element(value='') {
  return {value, hidden:false, disabled:false, checked:false, readOnly:false, textContent:'', dataset:{}, listeners:{},
    addEventListener(type,fn){(this.listeners[type]??=[]).push(fn)},
    emit(type,event={}){for(const fn of this.listeners[type]??[]) fn(event)},
    removeAttribute(name){(this.removed??=[]).push(name)},
    focus(){this.focused=true}, closest(){return this.field??=(element())},
    setSelectionRange(a,b){this.selectionStart=a;this.selectionEnd=b}};
}
function run(file,document,window=element()){vm.runInNewContext(fs.readFileSync(`src/main/resources/static/js/${file}.js`,'utf8'),{document,window});}
function registration({mode='new',selected='',version='',unit='',candidates=true}={}) {
  const ids=Object.fromEntries(['registrationPicker','masterId','masterVersion','foodName','category','quantityUnit','foodSearch',
    'selectedFoodSummary','foodCandidates','foodSearchStatus','existingFoodPicker','foodNameField','sharedCategoryHelp',
    'selectedFoodName','selectedFoodCategory','registrationSubmit','quantityAmount','changeSelectedFood','searchFoods',
    'registrationCancel','singleRegistrationFields','bulkRegistrationPanel'].map(id=>[id,element()]));
  ids.foodName.value='새 두부';ids.category.value='새 분류';ids.masterId.value=selected;ids.masterVersion.value=version;
  ids.quantityUnit.value=unit;ids.registrationPicker.dataset.listUrl='/inventory';
  let current=mode;
  const radios=[element('new'),element('existing'),element('bulk')];
  const choices=candidates?['두부','두부','MILK'].map((name,i)=>{
    const b=element();b.dataset={masterId:String(i+1),name,category:i===1?'':'반찬',version:'7',unit:i===1?'':'모',detailUrl:`/foods/${i+1}`};
    b.querySelector=()=>({textContent:`분류 ${i} · #${i+1}`});return b;
  }):[];
  ids.registrationPicker.querySelector=()=>({value:current});ids.registrationPicker.querySelectorAll=()=>radios;
  const window=element();
  run('food-registration',{getElementById:id=>ids[id],querySelectorAll:()=>choices},window);
  return {ids,choices,window,form:ids.registrationPicker.closest(),mode(value){current=value;radios.find(r=>r.value===value).emit('change')},
    search(value,enter=false){ids.foodSearch.value=value;ids.foodSearch.emit('input');
      if(enter) ids.foodSearch.emit('keydown',{key:'Enter',preventDefault(){}});else ids.searchFoods.emit('click')}};
}
test('registration: no automatic results, duplicate identities stay selectable, case-insensitive explicit search',()=>{
  const e=registration();e.mode('existing');assert.equal(e.ids.foodCandidates.hidden,true);
  e.ids.foodSearch.value='두부';e.ids.foodSearch.emit('input');assert.equal(e.ids.foodCandidates.hidden,true);
  e.search(' 두부 ');assert.equal(e.choices.filter(x=>!x.hidden).length,2);
  e.search('milk',true);assert.equal(e.choices[2].hidden,false);assert.equal(e.choices[0].hidden,true);
});
test('registration: empty and no-result searches, edited query hides stale results',()=>{
  const e=registration();e.mode('existing');e.search('두부');e.search('없는 음식');
  assert.match(e.ids.foodSearchStatus.textContent,/일치하는 음식이 없어/);assert.equal(e.ids.foodCandidates.hidden,true);
  e.search('  ');assert.equal(e.ids.foodSearchStatus.hidden,true);assert.equal(e.ids.foodSearch.focused,true);
  e.search('두부');e.ids.foodSearch.emit('input');assert.equal(e.ids.foodCandidates.hidden,true);
});
test('registration: Enter during Korean composition does not search or submit',()=>{
  const e=registration();e.mode('existing');e.ids.foodSearch.value='두부';let prevented=0;
  for(const event of [{isComposing:true},{keyCode:229}]) e.ids.foodSearch.emit('keydown',{key:'Enter',preventDefault(){prevented++},...event});
  assert.equal(prevented,2);assert.equal(e.ids.foodCandidates.hidden,true);
  e.ids.foodSearch.emit('keydown',{key:'Enter',preventDefault(){prevented++}});assert.equal(e.ids.foodCandidates.hidden,false);
});
test('registration: selection locks shared identity, proposes unit, clearing prevents submission',()=>{
  const e=registration();e.mode('existing');assert.equal(e.ids.registrationSubmit.disabled,true);
  e.choices[0].emit('click');assert.equal(e.ids.masterId.value,'1');assert.equal(e.ids.masterVersion.value,'7');
  assert.equal(e.ids.foodName.disabled,true);assert.equal(e.ids.category.readOnly,true);assert.equal(e.ids.quantityUnit.value,'모');
  assert.equal(e.ids.quantityAmount.focused,true);assert.equal(e.ids.registrationSubmit.disabled,false);
  e.ids.changeSelectedFood.emit('click');assert.equal(e.ids.masterId.value,'');assert.equal(e.ids.masterVersion.value,'');
  assert.equal(e.ids.registrationSubmit.disabled,true);assert.equal(e.ids.foodCandidates.hidden,true);
});
test('registration: switching modes preserves new identity and changes submitted field ownership',()=>{
  const e=registration();e.ids.foodName.value='사용자 입력';e.ids.foodName.emit('input');
  e.ids.category.value='채소';e.ids.category.emit('input');e.mode('existing');e.choices[0].emit('click');e.mode('new');
  assert.equal(e.ids.foodName.value,'사용자 입력');assert.equal(e.ids.category.value,'채소');
  assert.equal(e.ids.foodName.disabled,false);assert.equal(e.ids.masterId.disabled,true);
  e.mode('existing');assert.equal(e.ids.foodName.value,'두부');assert.equal(e.ids.masterId.disabled,false);
});
test('registration: category without value hides; absent default unit does not erase input',()=>{
  const e=registration({unit:'팩'});e.mode('existing');e.choices[1].emit('click');
  assert.equal(e.ids.category.closest().hidden,true);assert.equal(e.ids.quantityUnit.value,'팩');
});
test('registration: validation redisplay preserves stale version and submitted custom unit',()=>{
  const e=registration({mode:'existing',selected:'1',version:'3',unit:'사용자단위'});
  assert.equal(e.ids.masterVersion.value,'3');assert.equal(e.ids.quantityUnit.value,'사용자단위');
  assert.equal(e.ids.selectedFoodSummary.hidden,false);
});
test('registration: disappeared selection and empty registry cannot submit',()=>{
  for(const options of [{mode:'existing',selected:'999'},{mode:'existing',candidates:false}]) {
    const e=registration(options);assert.equal(e.ids.registrationSubmit.disabled,true);assert.equal(e.ids.selectedFoodSummary.hidden,true);
  }
});
test('registration: cancel follows current mode and selected food, including changing a preselected food',()=>{
  const e=registration({mode:'existing',selected:'1',version:'7'});
  assert.equal(e.ids.registrationCancel.href,'/foods/1');
  e.ids.changeSelectedFood.emit('click');assert.equal(e.ids.registrationCancel.href,'/inventory');
  e.choices[1].emit('click');assert.equal(e.ids.registrationCancel.href,'/foods/2');
  e.mode('new');assert.equal(e.ids.registrationCancel.href,'/inventory');
});
test('registration: first submission locks button, repeated submit is blocked and input stays enabled',()=>{
  const e=registration();let prevented=0;const event={preventDefault(){prevented++}};
  e.form.emit('submit',event);assert.equal(prevented,0);assert.equal(e.ids.registrationSubmit.disabled,true);
  assert.equal(e.ids.registrationSubmit.textContent,'등록하는 중…');assert.equal(e.ids.foodName.disabled,false);
  e.form.emit('submit',event);assert.equal(prevented,1);
  e.ids.foodName.value='브라우저 복원 이름';e.ids.category.value='브라우저 복원 분류';
  e.window.emit('pageshow',{persisted:true});assert.equal(e.ids.registrationSubmit.disabled,false);
  assert.equal(e.ids.foodName.value,'브라우저 복원 이름');assert.equal(e.ids.category.value,'브라우저 복원 분류');
  e.form.emit('submit',event);assert.equal(prevented,1);
});
test('registration: existing purchase Enter without selection is blocked, selected identity is submitted',()=>{
  const e=registration();e.mode('existing');let prevented=0;const event={preventDefault(){prevented++}};
  e.form.emit('submit',event);assert.equal(prevented,1);e.choices[0].emit('click');e.form.emit('submit',event);
  assert.equal(prevented,1);assert.equal(e.ids.masterId.disabled,false);assert.equal(e.ids.masterVersion.disabled,false);
  assert.equal(e.ids.registrationSubmit.disabled,true);
});

function foodForm() {
  const ids=Object.fromEntries(['sourceType','sourceMemoField','sourceMemo','freezerFields','frozenAt','freezeType',
    'deliveryStorageHelp','deliveryFreezeHelp'].map(id=>[id,element()]));
  const today=element();ids.frozenAt.max='2026-09-14';const radios=['FRIDGE','FREEZER','ROOM'].map(element);
  let storage;const option={isConnected:true,remove(){this.isConnected=false}};
  ids.freezeType.querySelector=()=>option;ids.freezeType.append=o=>{o.isConnected=true};
  ids.freezerFields.querySelectorAll=()=>[ids.freezeType,ids.frozenAt,today];
  const invalid=element();
  run('food-form',{getElementById:id=>ids[id],querySelectorAll:()=>radios,querySelector:q=>
    q.includes('storageType')?(storage?{value:storage}:null):q.includes('freezeToday')?today:invalid});
  return {ids,today,option,invalid,storage(value){storage=value;radios[0].emit('change')},source(value){ids.sourceType.value=value;ids.sourceType.emit('change')}};
}
test('form: delivery defaults only without explicit storage and forces home freezing',()=>{
  const e=foodForm();e.source('DELIVERY_LEFTOVER');assert.equal(e.ids.freezerFields.hidden,false);
  assert.equal(e.ids.freezeType.value,'HOME_FROZEN');assert.equal(e.option.isConnected,false);
  for(const location of ['FRIDGE','ROOM']) {e.storage(location);assert.equal(e.ids.freezerFields.hidden,true);assert.equal(e.ids.frozenAt.disabled,true)}
  e.storage('FREEZER');assert.equal(e.ids.freezerFields.hidden,false);e.source('PURCHASE');assert.equal(e.option.isConnected,true);
});
test('form: source memo is submitted only for explicit ETC; changing source preserves editable draft',()=>{
  const e=foodForm();e.source('ETC');e.ids.sourceMemo.value='선물';assert.equal(e.ids.sourceMemo.disabled,false);
  e.source('');assert.equal(e.ids.sourceMemo.disabled,true);e.source('ETC');assert.equal(e.ids.sourceMemo.value,'선물');
});
test('form: freeze-today uses server date; manual date clears checkbox; first invalid field focused',()=>{
  const e=foodForm();e.today.checked=true;e.today.emit('change');assert.equal(e.ids.frozenAt.value,'2026-09-14');
  e.ids.frozenAt.value='2026-09-10';e.ids.frozenAt.emit('input');assert.equal(e.today.checked,false);
  assert.equal(e.invalid.focused,true);
});
for(const [typed,afterInput,afterBlur] of [['1.239','1.23','1.23'],['.5','.5','0.5'],['2.','2.','2'],['','',''],['0','0','0'],
  ['-1','2','2'],['NaN','2','2'],['1e3','2','2'],['1,2','2','2'],['한글','2','2'],['1.2.3','2','2']]) {
  test(`quantity input: ${JSON.stringify(typed)} preserves decimal input contract`,()=>{
    const input=element('2'),unit=element('개');
    run('quantity-input',{getElementById:id=>id==='quantityUnit'?unit:input});
    input.value=typed;input.selectionStart=typed.length;input.emit('input');assert.equal(input.value,afterInput);
    input.emit('blur');assert.equal(input.value,afterBlur);
  });
}
test('quantity unit: 4-char cap replaces maxlength and waits for IME composition',()=>{
  const amount=element('2'),unit=element('');
  run('quantity-input',{getElementById:id=>id==='quantityUnit'?unit:amount});
  // maxlength truncates mid-composition, so the script must take over the cap.
  assert.deepEqual(unit.removed,['maxlength']);
  unit.value='조각조각';unit.emit('input');assert.equal(unit.value,'조각조각');
  unit.value='조각조각들';unit.emit('input');assert.equal(unit.value,'조각조각');
  assert.equal(unit.selectionStart,4);
  unit.value='조각조각들';unit.emit('input',{isComposing:true});assert.equal(unit.value,'조각조각들');
  unit.emit('compositionend');assert.equal(unit.value,'조각조각');
});

test('registration: bulk panel is exclusive and preserves the single-item draft',()=>{
  const e=registration();e.ids.foodName.value='내 두부';e.ids.foodName.emit('input');
  e.mode('bulk');assert.equal(e.ids.singleRegistrationFields.hidden,true);assert.equal(e.ids.bulkRegistrationPanel.hidden,false);assert.equal(e.ids.existingFoodPicker.hidden,true);
  let prevented=false;e.form.emit('submit',{preventDefault(){prevented=true}});assert.equal(prevented,true);
  e.mode('new');assert.equal(e.ids.singleRegistrationFields.hidden,false);assert.equal(e.ids.bulkRegistrationPanel.hidden,true);assert.equal(e.ids.foodName.value,'내 두부');
  e.mode('existing');assert.equal(e.ids.existingFoodPicker.hidden,false);assert.equal(e.ids.bulkRegistrationPanel.hidden,true);
});
