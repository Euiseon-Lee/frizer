(() => {
    const firstInvalidField = document.querySelector('[aria-invalid="true"]:not(:disabled)');
    if (firstInvalidField) firstInvalidField.focus();
})();
