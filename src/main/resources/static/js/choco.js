(() => {
'use strict';
if(window.frizerChocoInitialized)return;
window.frizerChocoInitialized=true;
let storage;
try{storage=window.sessionStorage;}catch(_){}
const selector=ChocoSelector.createSelector(storage);
let page=selector.createPage();
let context=location.pathname+location.search;
let scheduled=false;
const loads=new WeakMap();
// Keep the slot in layout, but do not paint partially downloaded images.
function loadImage(img,url,onFailure){
    if(loads.get(img)?.url===url)return;
    const state={url,failed:false};
    loads.set(img,state);
    img.classList.add('choco-loading');
    img.decoding='async';
    const current=()=>loads.get(img)===state;
    const reveal=()=>{if(current()&&!state.failed)img.classList.remove('choco-loading');};
    const fail=()=>{
        if(!current()||state.failed)return;
        state.failed=true;
        onFailure();
    };
    img.onerror=fail;
    img.onload=()=>{
        if(!current())return;
        if(typeof img.decode==='function')img.decode().then(reveal,fail);
        else reveal();
    };
    if(img.src!==url)img.src=url;
    // A cached image may already be complete before a load event is observed.
    if(img.complete&&img.naturalWidth>0)img.onload();
}
function render(){
    scheduled=false;
    const nextContext=location.pathname+location.search;
    if(context!==nextContext){context=nextContext;page=selector.createPage();}
    const images=[...document.querySelectorAll('img[data-choco-region]')]
        .filter(img=>img.parentElement.getClientRects().length && !img.parentElement.closest('[hidden]'));
    const regions=images.map(img=>({id:img.dataset.chocoRegion,role:img.dataset.chocoRole}));
    const choices=page.sync(regions);
    for(const img of images){
        const key=choices.get(img.dataset.chocoRegion);
        const keepFailedSlot=!key&&page.loadExhausted(img.dataset.chocoRegion);
        const hide=!key&&!keepFailedSlot;
        img.classList.toggle('choco-load-failed',keepFailedSlot);
        if(!key){
            loads.delete(img);img.onload=null;img.onerror=null;
            img.classList.remove('choco-loading');
            if(img.hidden!==hide)img.hidden=hide;
            img.removeAttribute('src');delete img.dataset.chocoKey;continue;
        }
        const asset=ChocoSelector.assets[key];
        img.classList.toggle('choco-portrait',asset.renderMode==='portrait');
        img.classList.toggle('choco-left-edge',asset.renderMode==='left-edge');
        img.classList.toggle('choco-wide',asset.renderMode==='wide');
        const base=new URL(img.dataset.chocoBase||'/assets/choco/',location.href);
        const url=new URL('web/v1/'+encodeURIComponent(key+'.webp'),base).href;
        img.dataset.chocoKey=key;
        img.dataset.poseGroup=asset.poseGroup;
        img.dataset.emotionGroup=asset.emotionGroup;
        loadImage(img,url,()=>{
            page.fail(img.dataset.chocoRegion,key);
            schedule();
        });
        if(img.hidden)img.hidden=false;
    }
    for(const img of document.querySelectorAll('img[data-choco-static]')){
        if(img.dataset.chocoStatic!==undefined)
            loadImage(img,img.src,()=>img.classList.add('choco-load-failed'));
    }
}
function schedule(){if(!scheduled){scheduled=true;queueMicrotask(render);}}
render();
new MutationObserver(schedule).observe(document.body,{
    childList:true,subtree:true,attributes:true,attributeFilter:['hidden','class','style','data-choco-role','data-choco-region']
});
window.addEventListener('popstate',schedule);
window.addEventListener('pageshow',event=>{if(event.persisted){page=selector.createPage();schedule();}});
})();
