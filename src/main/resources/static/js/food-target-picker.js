(() => {
    const picker = document.getElementById('mergePicker');
    const select = document.getElementById('targetId');
    if (!picker || !select) return;
    const summary = picker.querySelector('summary');
    const label = picker.querySelector('#mergeSelectedLabel');
    const options = [...picker.querySelectorAll('button[data-value]')];
    function sync() {
        const selected = options.find(button => button.dataset.value === select.value) || options[0];
        label.textContent = selected.textContent;
        summary.title = selected.textContent;
        options.forEach(button => button.setAttribute('aria-pressed', String(button === selected)));
    }
    options.forEach(button => button.addEventListener('click', () => {
        if (select.disabled) return;
        select.value = button.dataset.value;
        sync();
        picker.open = false;
        summary.focus();
        select.dispatchEvent(new Event('change', {bubbles: true}));
    }));
    summary.addEventListener('click', event => {
        if (select.disabled) event.preventDefault();
    });
    picker.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            event.preventDefault(); picker.open = false; summary.focus(); return;
        }
        if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key) || select.disabled) return;
        event.preventDefault(); picker.open = true;
        const index = options.indexOf(document.activeElement);
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
            : event.key === 'ArrowDown' ? (index + 1) % options.length
            : (index <= 0 ? options.length - 1 : index - 1);
        options[next].focus();
    });
    document.addEventListener('click', event => {
        if (!picker.contains(event.target)) picker.open = false;
    });
    select.addEventListener('change', sync);
    window.addEventListener('pageshow', () => { picker.open = false; sync(); });
    sync();
    select.hidden = true;
    picker.hidden = false;
})();
