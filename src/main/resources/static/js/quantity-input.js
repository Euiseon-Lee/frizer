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