// Use one fixed-height input and an overlaid hint, never two layout rows on iOS.
(() => {
    const sync = () => {
        for (const input of document.querySelectorAll('input[type=date]')) {
            if (!input.parentElement.classList.contains('date-input-wrap')) {
                const wrap = document.createElement('span');
                wrap.className = 'date-input-wrap';
                input.before(wrap);
                wrap.append(input);
            }
            input.classList.toggle('date-empty', !input.value);
        }
    };
    document.addEventListener('input', sync);
    document.addEventListener('change', sync);
    window.addEventListener('pageshow', sync);
    sync();
})();
