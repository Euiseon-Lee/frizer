(() => {
    'use strict';
    const form=document.getElementById('moveSelectForm');
    if(!form) return;
    const button=document.getElementById('moveSelected');
    const sync=()=>{button.disabled=!form.querySelector('input[name=items]:checked');};
    form.addEventListener('change',event=>{if(event.target.name==='items') sync();});
    window.addEventListener('pageshow',sync);
    sync();
})();
