(() => {
    'use strict';
    const root=document.getElementById('itemMove');
    if(!root) return;
    const find=id=>document.getElementById(id), base=root.dataset.baseUrl;
    const status=find('moveStatus'), preview=find('itemMovePreview'), retry=find('moveRetry');
    const target=find('moveTarget'), form=find('itemMoveSubmit');
    const items=root.dataset.items.split(',');
    let revision=0, searchRevision=0, current=null, submitting=false, timer, retryAction;
    const mode=()=>root.querySelector('input[name=mode]:checked')?.value;
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
        if(!response.ok) throw new Error(data.message||'병합 내용을 불러오지 못했어. 다시 시도해줘.');
        return data;
    }
    async function loadPreview() {
        if(submitting) return;
        invalidate();
        if(!mode()) return;
        const query=new URLSearchParams({mode:mode()});
        for(const item of items) query.append('items',item);
        if(mode()==='EXISTING') {if(!target.value) return;query.set('targetId',target.value);}
        else if(find('moveName').value.trim()) {query.set('newName',find('moveName').value);query.set('newCategory',find('moveCategory').value);}
        const ownRevision=revision;
        status.textContent='병합할 내용을 확인하고 있어.';
        try {
            const result=await get(base+'/preview?'+query);
            if(ownRevision!==revision || submitting) return;
            current=result;
            find('moveSourceName').textContent=result.source.foodName;
            root.querySelector('[data-result="moveCount"]').textContent=(result.items.length+(result.whole?result.endedCount:0))+'건';
            root.querySelector('[data-result="historyCount"]').textContent=result.historyCount+'건';
            const remaining=root.querySelector('[data-result="remainingCount"]');
            if(remaining) remaining.textContent=result.remainingCount+'건';
            // The form submits the live controls; only the server-known target version is carried over.
            if(result.command.targetVersion!=null) {
                const input=document.createElement('input');
                input.type='hidden';input.name='targetVersion';input.value=String(result.command.targetVersion);
                find('moveFields').append(input);
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
    root.querySelectorAll('input[name=mode]').forEach(input=>input.addEventListener('change',()=>{
        searchRevision++;invalidate();find('existingMove').hidden=mode()!=='EXISTING';find('newMove').hidden=mode()!=='NEW';loadPreview();
    }));
    retry.addEventListener('click',()=>retryAction&&retryAction());
    form.addEventListener('submit',event=>{
        if(submitting || !current){event.preventDefault();return;}
        submitting=true;
        // Form-associated controls stay enabled so their live values submit; disabling would drop them.
        root.querySelectorAll('input:not([type=hidden]):not([form]),select:not([form]),button').forEach(control=>control.disabled=true);
        form.querySelector('button').textContent='병합하는 중…';
        preview.setAttribute('aria-busy','true');
    });
    window.addEventListener('pageshow',event=>{if(event.persisted) window.location.reload();});
})();
