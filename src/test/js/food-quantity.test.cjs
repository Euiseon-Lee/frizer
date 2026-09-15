const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
test('click and Enter submissions are locked; history restore rechecks server state',()=>{
  const handlers={},button={disabled:false};let reloads=0,prevented=0;
  const form={addEventListener:(name,fn)=>handlers[name]=fn,querySelector:()=>button};
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/food-quantity.js','utf8'),{
    document:{querySelector:()=>form,getElementById:()=>null},window:{addEventListener:(name,fn)=>handlers[name]=fn,location:{reload:()=>reloads++}}
  });
  handlers.submit({preventDefault:()=>prevented++});assert.equal(button.disabled,true);assert.equal(prevented,0);
  handlers.submit({preventDefault:()=>prevented++});assert.equal(prevented,1);
  handlers.pageshow({persisted:false});assert.equal(reloads,0);
  handlers.pageshow({persisted:true});assert.equal(reloads,1);
});
test('numeric input rejects negatives, text, exponents, excess precision and amounts above stock',()=>{
  const handlers={},question={},error={};
  const input={value:'2.5',dataset:{maximum:'2.5',unit:'모',action:'CONSUME'},addEventListener:(name,fn)=>handlers[name]=fn,setCustomValidity(message){this.validationMessage=message;}};
  const form={addEventListener:()=>{},querySelector:()=>({})};
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/food-quantity.js','utf8'),{
    document:{querySelector:()=>form,getElementById:id=>({quantityAmount:input,quantityQuestion:question,quantityInputError:error})[id]},window:{addEventListener:()=>{}}
  });
  assert.equal(question.textContent,'이 구매 항목을 이렇게 기록할까?');
  for(const value of ['-1','3','1e0','한글','1,5','0.0001']){input.value=value;handlers.input();assert.equal(input.value,'2.5');}
  let blocked=false;handlers.beforeinput({data:'-',preventDefault:()=>blocked=true});assert.equal(blocked,true);
  input.value='0';handlers.input();assert.notEqual(input.validationMessage,'');
  input.value='0.125';handlers.input();assert.equal(input.validationMessage,'');assert.equal(question.textContent,'이 구매 항목을 이렇게 기록할까?');
  input.value='';handlers.input();handlers.blur();assert.notEqual(error.textContent,'');
});
