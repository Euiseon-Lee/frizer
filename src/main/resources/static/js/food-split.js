(() => {
    'use strict';
    const fields = document.getElementById('freezerFields');
    if (!fields) return;
    const frozenAt = document.getElementById('frozenAt');
    const freezeToday = document.querySelector('input[name="freezeToday"]');
    function sync() {
        const storage = document.querySelector('input[name="newStorage"]:checked')?.value;
        fields.hidden = storage !== 'FREEZER';
        fields.querySelectorAll('input, select').forEach(input => { input.disabled = fields.hidden; });
    }
    document.querySelectorAll('input[name="newStorage"]').forEach(input => input.addEventListener('change', sync));
    // max is rendered by the server using its Asia/Seoul Clock.
    freezeToday.addEventListener('change', () => { if (freezeToday.checked) frozenAt.value = frozenAt.max; });
    ['input', 'change'].forEach(type => frozenAt.addEventListener(type, () => { freezeToday.checked = false; }));
    sync();
})();
