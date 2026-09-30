(() => {
    const forms = [...document.querySelectorAll('[data-warning-settings]')];
    const previousDates = new WeakMap();
    function sync(root) {
        const toggle = root.querySelector('[data-warning-toggle]');
        const active = !toggle || toggle.checked;
        const forever = root.querySelector('[data-warning-forever]');
        const date = root.querySelector('[data-warning-date]');
        const fields = root.querySelector('[data-warning-fields]');
        if (forever.checked) {
            if (!previousDates.has(date)) previousDates.set(date, date.value);
            date.value = '9999-12-31';
        } else if (previousDates.has(date)) {
            date.value = previousDates.get(date);
            previousDates.delete(date);
        }
        date.classList.toggle('date-empty', !date.value);
        if (fields) fields.hidden = !active;
        forever.disabled = !active;
        date.disabled = !active || forever.checked;
        date.required = active && !forever.checked;
    }
    forms.forEach(root => { root.addEventListener('change', () => sync(root)); sync(root); });
    window.addEventListener('pageshow', () => forms.forEach(sync));
    const dialog = document.querySelector('[data-warning-dialog]');
    const open = document.querySelector('[data-warning-open]');
    if (!dialog || !open || !dialog.showModal) return;
    open.hidden = false;
    open.addEventListener('click', () => dialog.showModal());
    const cancel = () => { dialog.querySelector('form').reset(); forms.forEach(root => previousDates.delete(root.querySelector('[data-warning-date]'))); forms.forEach(sync); document.dispatchEvent(new Event('change')); };
    dialog.querySelector('[data-warning-close]').addEventListener('click', () => { cancel(); dialog.close(); });
    dialog.addEventListener('cancel', cancel);
    dialog.addEventListener('close', () => open.focus());
    if (dialog.dataset.warningReopen === 'true') dialog.showModal();
})();
