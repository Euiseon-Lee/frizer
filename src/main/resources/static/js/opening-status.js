(() => {
    const state = document.querySelector('[data-opening-status]');
    const date = document.querySelector('[data-opening-date]');
    const hint = document.querySelector('[data-opening-hint]');
    if (!state || !date || !hint) return;
    const sync = () => { date.disabled = state.value !== 'OPENED'; hint.hidden = date.disabled; };
    state.addEventListener('change', sync);
    window.addEventListener('pageshow', sync);
    sync();
})();
