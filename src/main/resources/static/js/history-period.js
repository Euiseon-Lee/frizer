(() => {
    const card = document.querySelector('.history-period');
    if (!card) return;
    const toggle = card.querySelector('.history-period-toggle');
    const panel = card.querySelector('.history-period-panel');
    const openInput = card.querySelector('[name=searchOpen]');
    function setOpen(open) {
        toggle.setAttribute('aria-expanded', String(open));
        panel.hidden = !open;
        openInput.value = String(open);
    }
    toggle.addEventListener('click', () => setOpen(panel.hidden));
    const presets = card.querySelectorAll('.history-period-presets button');
    const start = card.querySelector('[name=start]');
    const end = card.querySelector('[name=end]');
    const period = card.querySelector('#historyPeriod');
    period.disabled = false;
    function selectPeriod(value) {
        period.value = value;
        presets.forEach(button => button.setAttribute('aria-pressed', String(button.value === value)));
    }
    presets.forEach(button => button.addEventListener('click', () => {
        const value = button.value;
        start.disabled = end.disabled = value === 'all';
        if (value === 'all') {
            start.value = end.value = '';
        } else if (value !== 'custom') {
            start.value = button.dataset.start;
            end.value = button.dataset.end;
        }
        selectPeriod(value);
    }));
    function syncPeriodSelection() {
        const matching = Array.from(presets).find(button =>
            button.dataset.start === start.value && button.dataset.end === end.value);
        const selected = !start.value && !end.value ? 'all' : (matching ? matching.value : 'custom');
        selectPeriod(selected);
    }
    panel.querySelectorAll('input[type=date]').forEach(input => {
        input.addEventListener('input', syncPeriodSelection);
        input.addEventListener('change', syncPeriodSelection);
    });
    // A hidden invalid date must be revealed before the browser focuses it.
    panel.addEventListener('invalid', () => setOpen(true), true);
})();
