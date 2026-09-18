// Keep typing and pasted quantities to two decimal places without rounding.
const quantityInput = document.getElementById('quantityAmount');
let previousQuantity = quantityInput.value;
quantityInput.addEventListener('input', () => {
    const caret = quantityInput.selectionStart;
    const match = quantityInput.value.match(/^(\d*)(?:\.(\d*))?$/);
    let next = previousQuantity;
    if (match) {
        next = match[1] + (match[2] === undefined ? '' : '.' + match[2].slice(0, 2));
    }
    if (quantityInput.value !== next) {
        quantityInput.value = next;
        const position = Math.min(caret ?? next.length, next.length);
        quantityInput.setSelectionRange(position, position);
    }
    previousQuantity = next;
});
quantityInput.addEventListener('blur', () => {
    if (quantityInput.value.startsWith('.')) quantityInput.value = '0' + quantityInput.value;
    if (quantityInput.value.endsWith('.')) quantityInput.value = quantityInput.value.slice(0, -1);
    previousQuantity = quantityInput.value;
});

// Cap the unit at 4 characters. The maxlength attribute truncates while an IME
// is still composing and leaves broken jamo behind, so with JS available the
// attribute comes off and the cap is applied only once composition settles.
const unitInput = document.getElementById('quantityUnit');
if (unitInput) {
    unitInput.removeAttribute('maxlength');
    const capUnit = () => {
        const chars = [...unitInput.value];
        if (chars.length > 4) {
            unitInput.value = chars.slice(0, 4).join('');
            unitInput.setSelectionRange(unitInput.value.length, unitInput.value.length);
        }
    };
    unitInput.addEventListener('input', event => { if (!event.isComposing) capUnit(); });
    unitInput.addEventListener('compositionend', capUnit);
}