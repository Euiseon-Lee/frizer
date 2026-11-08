(() => {
    const link = document.querySelector('[data-registration-menu]');
    const dialog = document.getElementById('registrationMenu');
    if (!link || !dialog || typeof dialog.showModal !== 'function') return;
    link.setAttribute('aria-haspopup', 'dialog');
    link.addEventListener('click', event => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault(); dialog.showModal();
    });
    dialog.querySelector('[data-registration-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
        if (event.target !== dialog) return;
        const rect = dialog.getBoundingClientRect();
        if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
    });
})();
