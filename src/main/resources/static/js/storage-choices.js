(() => {
    for (const group of document.querySelectorAll('[data-storage-choices]')) {
        const radios = [...group.querySelectorAll('input[name="storageType"]')];
        const error = document.getElementById(group.dataset.errorId);
        const freezer = document.getElementById(group.dataset.freezerFields);
        const source = document.getElementById(group.dataset.sourceField);
        function setInvalid(invalid) {
            group.setAttribute('aria-invalid', String(invalid));
            for (const radio of radios) {
                radio.classList.toggle('invalid', invalid);
                radio.setAttribute('aria-invalid', String(invalid));
                if (invalid) radio.setAttribute('aria-describedby', error.id);
                else radio.removeAttribute('aria-describedby');
            }
            if (invalid) group.setAttribute('aria-describedby', error.id);
            else group.removeAttribute('aria-describedby');
            error.hidden = !invalid;
        }
        function update() {
            const selected = radios.find(radio => radio.checked)?.value;
            // Delivery leftovers keep the existing implicit-freezer rule; explicit choices win.
            const effective = selected || (source?.value === 'DELIVERY_LEFTOVER' ? 'FREEZER' : null);
            if (effective) setInvalid(false);
            if (freezer) {
                freezer.hidden = effective !== 'FREEZER';
                freezer.querySelectorAll('input, select').forEach(input => {input.disabled = freezer.hidden;});
            }
        }
        group.addEventListener('invalid', event => {
            if (radios.includes(event.target)) setInvalid(true);
        }, true);
        for (const radio of radios) radio.addEventListener('change', update);
        source?.addEventListener('change', update);
        window.addEventListener('pageshow', update);
        update();
    }
})();
