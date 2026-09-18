// Every server round-trip can be slow on the free tier, so buttons give feedback
// app-wide: a POST submit locks its button with the shared spinner and swallows
// repeated submits, and full-page navigation buttons show the spinner without
// blocking the browser. Screens with their own submit guards run first (element
// listeners precede this document-level one) and a prevented submit is left alone.
(() => {
    const lock = button => {
        button.dataset.busyLock = 'true';
        button.classList.add('busy-indicator');
    };
    document.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        const form = event.target;
        if ((form.getAttribute('method') || 'get').toLowerCase() !== 'post') return;
        const button = event.submitter && event.submitter.tagName === 'BUTTON' ? event.submitter
            : form.querySelector('button[type="submit"], button:not([type])');
        if (!button) return;
        if (button.dataset.busyLock) { event.preventDefault(); return; }
        lock(button);
        button.disabled = true;
    });
    document.addEventListener('click', event => {
        const link = event.target.closest?.('a.button, a.cancel-link');
        if (!link || event.defaultPrevented) return;
        if (link.target || link.hasAttribute('download') || link.getAttribute('aria-disabled') === 'true') return;
        lock(link);
    });
    // A BFCache return restores the locked look; hand every button back.
    window.addEventListener('pageshow', () => {
        for (const button of document.querySelectorAll('[data-busy-lock]')) {
            button.disabled = false;
            button.classList.remove('busy-indicator');
            delete button.dataset.busyLock;
        }
    });
})();
