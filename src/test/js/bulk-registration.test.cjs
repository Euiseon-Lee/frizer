const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
function screen() {
 const make=()=>({listeners:{},textContent:'',hidden:false,disabled:false,addEventListener(n,f){this.listeners[n]=f},setAttribute(){},removeAttribute(){}});
 const input=make();input.files=[];input.setCustomValidity=v=>input.validity=v;
 const upload=make();upload.action='/inventory/bulk/preview';upload.reportValidity=()=>!input.validity;
 const submit=make(),token={value:'old'},message=make(),results=make(),complete=make(),help=make();
 let preview;
 const nodes={bulkFile:input,bulkUploadMessage:message,bulkResults:results,bulkComplete:complete,bulkCompletionHelp:help};
 upload.querySelector=s=>s.includes('button')?submit:token;
 results.replaceChildren=(...children)=>{results.children=children;nodes.bulkCommitForm=page.valid?{}:null;nodes.bulkRepeat=page.duplicate?{checked:false}:null;preview=children.length?{scrollIntoView(v){preview.scrolled=v},focus(v){preview.focused=v}}:null;};
 const pending=[];
 const page={childNodes:['new-preview'],token:'new',error:'',valid:true,duplicate:false};
 class Parser{parseFromString(){return {getElementById:id=>id==='bulkResults'?page:id==='bulkUploadMessage'?{textContent:page.error}:null,querySelector:()=>({value:page.token})};}}
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/bulk-registration.js','utf8'),{
  document:{getElementById:id=>id==='bulkPreview'?preview:nodes[id],querySelector:()=>upload},
  fetch:(url,options)=>new Promise((resolve,reject)=>pending.push({url,options,resolve,reject})),FormData:class{},AbortController,DOMParser:Parser
 });
 return {input,upload,submit,token,message,results,complete,help,pending,page,nodes,get preview(){return preview},change(){return input.listeners.change()},send(){let prevented=false;const done=upload.listeners.submit({preventDefault(){prevented=true}});assert.ok(prevented);return done;},ok(i=0){pending[i].resolve({ok:true,text:async()=>'<html>'})}};
}
test('file selection automatically previews and invalidates old commit',async()=>{
 const s=screen();s.input.files=[{}];const done=s.change();
 assert.equal(s.pending.length,1);assert.equal(s.message.textContent,'미리보기를 준비하고 있어.');assert.equal(s.complete.disabled,true);assert.equal(s.help.hidden,true);
 let stopped=false;s.results.listeners.submit({preventDefault(){stopped=true}});assert.ok(stopped);
 s.ok();await done;
});
test('empty selection and multiple files never upload',async()=>{
 const s=screen();await s.change();assert.equal(s.pending.length,0);
 s.input.files=[{},{}];await s.change();assert.equal(s.pending.length,0);assert.match(s.input.validity,/1개/);
 await s.send();assert.equal(s.pending.length,0);
});
test('automatic preview preserves file, replaces results and focuses section 02',async()=>{
 const s=screen();const file={};s.input.files=[file];const done=s.change();s.ok();await done;
 assert.equal(s.input.files[0],file);assert.deepEqual(s.results.children,['new-preview']);assert.equal(s.token.value,'new');assert.equal(s.preview.scrolled.block,'start');assert.equal(s.preview.focused.preventScroll,true);assert.equal(s.message.hidden,true);
});
test('server session error updates token and allows selecting the same file again',async()=>{
 const s=screen();s.page.childNodes=[];s.page.error='파일을 다시 선택해줘.';s.input.files=[{}];const done=s.change();s.ok();await done;
 assert.equal(s.token.value,'new');assert.equal(s.message.textContent,s.page.error);assert.equal(s.preview,null);assert.equal(s.input.value,'');
 s.page.error='';const retry=s.change();assert.equal(s.pending.length,2);s.ok(1);await retry;
});
test('late response cannot overwrite the replacement file preview',async()=>{
 const s=screen();s.input.files=[{}];const first=s.change();s.input.files=[{}];const second=s.change();
 assert.equal(s.pending[0].options.signal.aborted,true);s.ok(1);await second;
 s.page.childNodes=['stale'];s.ok(0);await first;assert.deepEqual(s.results.children,['new-preview']);
});
test('network failure keeps commit disabled and permits reselecting the same file',async()=>{
 const s=screen();s.input.files=[{}];const done=s.change();s.pending[0].reject(new Error('offline'));await done;
 assert.equal(s.complete.disabled,true);assert.equal(s.input.value,'');assert.match(s.message.textContent,/파일을 다시 선택해줘/);
 const retry=s.change();s.ok(1);await retry;assert.equal(s.message.hidden,true);
});
test('clearing a file hides previous preview and blocks an in-flight result',async()=>{
 const s=screen();s.input.files=[{}];const first=s.change();s.ok();await first;
 const next=s.change();assert.equal(s.preview.hidden,true);
 s.input.files=[];await s.change();assert.equal(s.complete.disabled,true);assert.equal(s.message.hidden,true);
 s.page.childNodes=['stale'];s.ok(1);await next;assert.deepEqual(s.results.children,['new-preview']);assert.equal(s.preview.hidden,true);
});

test('duplicate confirmation controls commit and cannot revive an invalidated preview',async()=>{
 const s=screen();s.page.duplicate=true;s.input.files=[{}];const done=s.change();s.ok();await done;
 assert.equal(s.complete.disabled,true);
 const repeat=s.nodes.bulkRepeat;repeat.checked=true;s.results.listeners.change({target:repeat});assert.equal(s.complete.disabled,false);
 repeat.checked=false;s.results.listeners.change({target:repeat});assert.equal(s.complete.disabled,true);
 s.input.files=[];await s.change();repeat.checked=true;s.results.listeners.change({target:repeat});assert.equal(s.complete.disabled,true);
});
test('nonduplicate preview enables commit without a confirmation checkbox',async()=>{
 const s=screen();s.input.files=[{}];const done=s.change();s.ok();await done;assert.equal(s.complete.disabled,false);
});
