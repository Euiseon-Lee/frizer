(() => {
    'use strict';
    const form=document.getElementById('moveSelectForm');
    if(!form) return;
    const buttons=['moveSelected','deleteSelected']
        .map(id=>document.getElementById(id)).filter(Boolean);
    const sync=()=>{
        const none=!form.querySelector('input[name=items]:checked');
        buttons.forEach(button=>{button.disabled=none;});
    };
    form.addEventListener('change',event=>{if(event.target.name==='items') sync();});
    window.addEventListener('pageshow',sync);
    sync();
})();
