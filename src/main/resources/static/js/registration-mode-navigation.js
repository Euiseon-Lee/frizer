(() => {
    document.querySelectorAll('#bulkRegistrationModes [data-mode-url]').forEach(input => {
        input.addEventListener('change', () => { if (input.checked) window.location.assign(input.dataset.modeUrl); });
    });
})();
