(() => {
    'use strict';
    const root=document.getElementById('itemMove');
    if(!root) return;
    const find=id=>document.getElementById(id), base=root.dataset.baseUrl;
    const status=find('moveStatus'), preview=find('itemMovePreview'), retry=find('moveRetry');
    const target=find('moveTarget'), form=find('itemMoveSubmit');
    let revision=0, searchRevision=0, current=null, submitting=false, timer, retryAction;
    const mode=()=>root.querySelector('input[name=moveMode]:checked')?.value;
    function setChoices(choices=[]) {
        target.replaceChildren(new Option('음식을 골라줘',''));
        const options=find('itemMoveOptions');options.replaceChildren();
        const rows=[{foodName:'음식을 골라줘',masterId:null},...choices];
        for(const choice of rows) {
            const label=choice.foodName+(choice.masterId!=null && choice.category?' · '+choice.category:'');
            const value=choice.masterId==null?'':String(choice.masterId);
            if(value) target.add(new Option(label,value));
            const button=document.createElement('button'),text=document.createElement('span');
            button.type='button';button.dataset.value=value;button.title=label;
            text.className='merge-option-text';text.textContent=label;button.append(text);options.append(button);
        }
        target.dispatchEvent(new Event('optionschange'));
    }
    function invalidate() {
        revision++; current=null; clearTimeout(timer); preview.hidden=true;
        find('moveFields').replaceChildren(); retry.hidden=true; status.textContent='';
    }
    async function get(url) {
        const response=await fetch(url,{credentials:'same-origin',headers:{Accept:'application/json'}});
        const data=await response.json();
        if(!response.ok) throw new Error(data.message||'이동 내용을 불러오지 못했어. 다시 시도해줘.');
        return data;
    }
    async function loadPreview() {
        if(submitting) return;
        invalidate();
        if(!mode()) return;
        const query=new URLSearchParams({mode:mode()});
        if(mode()==='EXISTING') {if(!target.value) return;query.set('targetId',target.value);}
        else {if(!find('moveName').value.trim()) return;query.set('newName',find('moveName').value);query.set('newCategory',find('moveCategory').value);}
        const ownRevision=revision;
        status.textContent='이동할 내용을 확인하고 있어.';
        try {
            const result=await get(base+'/preview?'+query);
            if(ownRevision!==revision || submitting) return;
            current=result;
            find('moveSourceName').textContent=result.source.foodName;
            root.querySelector('[data-result="historyCount"]').textContent=result.historyCount+'건';
            root.querySelector('[data-result="sourceCount"]').textContent=(result.remainingCount+1)+'건';
            for(const [name,value] of Object.entries({...result.command,requestId:result.requestId})) {
                if(value==null) continue;
                const input=document.createElement('input'); input.type='hidden';input.name=name;input.value=String(value);find('moveFields').append(input);
            }
            preview.hidden=false;status.textContent='';
        } catch(error) {
            if(ownRevision!==revision || submitting) return;
            current=null;find('moveFields').replaceChildren();preview.hidden=true;
            status.textContent=error.message; retryAction=loadPreview;retry.hidden=false;
        }
    }
    async function search(event) {
        if(event) event.preventDefault();
        if(submitting) return;
        invalidate(); const ownSearch=++searchRevision;
        setChoices();find('moveResults').hidden=true;
        const q=find('moveQuery').value.trim();if(!q) return;
        status.textContent='음식을 찾고 있어.';
        try {
            const choices=await get(base+'/choices?'+new URLSearchParams({q}));
            if(ownSearch!==searchRevision || submitting) return;
            setChoices(choices);
            find('moveResults').hidden=choices.length===0;
            status.textContent=choices.length?'':'검색한 음식이 없어. 다른 이름으로 찾아줘.';
        } catch(error) {if(ownSearch===searchRevision){status.textContent='음식을 불러오지 못했어. 다시 시도해줘.';retryAction=search;retry.hidden=false;}}
    }
    find('moveSearch').addEventListener('submit',search);
    find('moveQuery').addEventListener('input',()=>{searchRevision++;invalidate();setChoices();find('moveResults').hidden=true;});
    target.addEventListener('change',loadPreview);
    root.querySelectorAll('input[name=moveMode]').forEach(input=>input.addEventListener('change',()=>{
        searchRevision++;invalidate();find('existingMove').hidden=mode()!=='EXISTING';find('newMove').hidden=mode()!=='NEW';loadPreview();
    }));
    for(const id of ['moveName','moveCategory']) find(id).addEventListener('input',()=>{invalidate();timer=setTimeout(loadPreview,300);});
    retry.addEventListener('click',()=>retryAction&&retryAction());
    form.addEventListener('submit',event=>{
        if(submitting || !current){event.preventDefault();return;}
        submitting=true;
        root.querySelectorAll('input:not([type=hidden]),select,button').forEach(control=>control.disabled=true);
        form.querySelector('button').textContent='이동하는 중…';
        preview.setAttribute('aria-busy','true');
    });
    window.addEventListener('pageshow',event=>{if(event.persisted) window.location.reload();});
})();
