// iOS shows nothing in an empty appearance:none date input, so CSS draws a hint
// on .date-empty. Delegated listeners keep the class in sync even when another
// script fills the date (e.g. the freeze-today checkbox), and pageshow covers
// BFCache restores.
(() => {
    const sync = () => {
        for (const input of document.querySelectorAll('input[type=date]'))
            input.classList.toggle('date-empty', !input.value);
    };
    document.addEventListener('input', sync);
    document.addEventListener('change', sync);
    window.addEventListener('pageshow', sync);
    sync();
})();
