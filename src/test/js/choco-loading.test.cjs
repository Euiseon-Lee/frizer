const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
const adapter=fs.readFileSync('src/main/resources/static/js/choco.js','utf8');
function setup({cached=false,staticImage=false}={}){
 const classes=new Set(),decodes=[],tasks=[];
 let choice='first',failures=0,observer;
 const img={dataset:staticImage?{chocoStatic:''}:{chocoRegion:'header',chocoRole:'list-header'},
  src:staticImage?'https://frizer.test/assets/choco/web/v1/fixed.webp':'',hidden:!staticImage,
  complete:cached,naturalWidth:cached?100:0,
  parentElement:{getClientRects:()=>[{}],closest:()=>null},
  classList:{contains:k=>classes.has(k),add:k=>classes.add(k),remove:k=>classes.delete(k),
   toggle(k,on){if(on)classes.add(k);else classes.delete(k);}},
  removeAttribute(k){if(k==='src')this.src='';},
  decode:()=>new Promise((resolve,reject)=>decodes.push({resolve,reject}))};
 const page={sync:()=>new Map([['header',choice]]),loadExhausted:()=>!choice,
  fail(){failures++;choice=failures===1?'second':null;}};
 const sandbox={URL,console,location:{href:'https://frizer.test/inventory',pathname:'/inventory',search:''},
  document:{body:{},querySelectorAll:q=>q.includes('static')?(staticImage?[img]:[]):(staticImage?[]:[img])},
  ChocoSelector:{assets:{first:{},second:{}},createSelector:()=>({createPage:()=>page})},
  queueMicrotask:fn=>tasks.push(fn),MutationObserver:class{constructor(fn){observer=fn;}observe(){}},addEventListener(){}};
 sandbox.window=sandbox;vm.runInNewContext(adapter,sandbox);
 return {img,classes,decodes,change(v){choice=v;observer();this.flush();},
  flush(){while(tasks.length)tasks.shift()();},get failures(){return failures;}};
}
test('keeps layout and hides image until decoding completes',async()=>{
 const s=setup();assert.equal(s.img.hidden,false);assert.ok(s.classes.has('choco-loading'));
 s.img.onload();assert.ok(s.classes.has('choco-loading'));
 s.decodes[0].resolve();await Promise.resolve();assert.ok(!s.classes.has('choco-loading'));
});
test('a stale decode or error cannot reveal or replace the newer selection',async()=>{
 const s=setup(),oldError=s.img.onerror;s.img.onload();s.change('second');
 s.decodes[0].resolve();await Promise.resolve();oldError();
 assert.ok(s.classes.has('choco-loading'));assert.equal(s.failures,0);
 s.img.onload();s.decodes[1].resolve();await Promise.resolve();assert.ok(!s.classes.has('choco-loading'));
});
test('decode and network failures retry once then retain an invisible slot',async()=>{
 const s=setup(),oldError=s.img.onerror;s.img.onload();s.decodes[0].reject(Error('decode'));
 await Promise.resolve();oldError();s.flush();assert.equal(s.failures,1);
 s.img.onerror();s.flush();assert.equal(s.failures,2);assert.equal(s.img.src,'');
 assert.equal(s.img.hidden,false);assert.ok(s.classes.has('choco-load-failed'));
});
test('cached and fixed login images also wait for decoding',async()=>{
 for(const staticImage of [false,true]){
  const s=setup({cached:true,staticImage});assert.equal(s.decodes.length,1);
  assert.ok(s.classes.has('choco-loading'));s.decodes[0].resolve();await Promise.resolve();
  assert.ok(!s.classes.has('choco-loading'));
 }
});
test('load event remains a fallback when decode is unavailable',()=>{
 const s=setup();s.img.decode=undefined;s.img.onload();assert.ok(!s.classes.has('choco-loading'));
});
