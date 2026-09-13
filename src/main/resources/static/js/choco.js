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
const failedFixed=new Set();
function render(){
    scheduled=false;
    const nextContext=location.pathname+location.search;
    if(context!==nextContext){context=nextContext;page=selector.createPage();}
    const images=[...document.querySelectorAll('img[data-choco-region]')]
        .filter(img=>img.parentElement.getClientRects().length && !img.parentElement.closest('[hidden]'));
    const regions=images.map(img=>({id:img.dataset.chocoRegion,role:img.dataset.chocoRole,fixed:img.dataset.chocoFixed||null}));
    const choices=page.sync(regions);
    for(const img of images){
        const key=choices.get(img.dataset.chocoRegion);
        const hide=!key||failedFixed.has(key);
        if(img.hidden!==hide)img.hidden=hide;
        if(!key){img.removeAttribute('src');delete img.dataset.chocoKey;continue;}
        const asset=ChocoSelector.assets[key];
        if(img.classList.contains('choco-portrait')!==(asset.renderMode==='portrait')) img.classList.toggle('choco-portrait',asset.renderMode==='portrait');
        if(img.classList.contains('choco-left-edge')!==(asset.renderMode==='left-edge')) img.classList.toggle('choco-left-edge',asset.renderMode==='left-edge');
        if(img.classList.contains('choco-wide'))img.classList.remove('choco-wide');
        const base=new URL(img.dataset.chocoBase||'/assets/choco/',location.href);
        const url=new URL(key+'.png',base).href;
        img.dataset.chocoKey=key;
        img.dataset.poseGroup=asset.poseGroup;
        img.dataset.emotionGroup=asset.emotionGroup;
        img.onerror=()=>{
            if(img.dataset.chocoFixed){failedFixed.add(key);img.hidden=true;return;}
            page.fail(img.dataset.chocoRegion,key);
            schedule();
        };
        if(img.src!==url)img.src=url;
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