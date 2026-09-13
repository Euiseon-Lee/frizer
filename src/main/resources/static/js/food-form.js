const storageInputs = document.querySelectorAll('input[name="storageType"]');
const sourceInput = document.getElementById('sourceType');
const sourceMemoField = document.getElementById('sourceMemoField');
const sourceMemo = document.getElementById('sourceMemo');
const freezerFields = document.getElementById('freezerFields');
const freezeToday = document.querySelector('input[name="freezeToday"]');
const frozenAt = document.getElementById('frozenAt');
const freezeType = document.getElementById('freezeType');
const commercialFreezeOption = freezeType.querySelector('option[value="COMMERCIAL_FROZEN"]');
function updateFreezerFields() {
    const storage = document.querySelector('input[name="storageType"]:checked')?.value;
    const isDelivery = sourceInput.value === 'DELIVERY_LEFTOVER';
    if (isDelivery) {
        freezeType.value = 'HOME_FROZEN';
        commercialFreezeOption.remove();
    } else if (!commercialFreezeOption.isConnected) {
        freezeType.append(commercialFreezeOption);
    }
    document.getElementById('deliveryStorageHelp').hidden = !isDelivery || storage !== undefined;
    document.getElementById('freezeTypeHelp').hidden = isDelivery;
    document.getElementById('deliveryFreezeHelp').hidden = !isDelivery;
    freezerFields.hidden = storage !== 'FREEZER' && !(storage === undefined && sourceInput.value === 'DELIVERY_LEFTOVER');
    freezerFields.querySelectorAll('input, select').forEach(input => { input.disabled = freezerFields.hidden; });
}
function updateSourceMemo() {
    sourceMemoField.hidden = sourceInput.value !== 'ETC';
    sourceMemo.disabled = sourceMemoField.hidden;
}
storageInputs.forEach(input => input.addEventListener('change', updateFreezerFields));
sourceInput.addEventListener('change', updateFreezerFields);
sourceInput.addEventListener('change', updateSourceMemo);
function applyToday() {
    // max is rendered by the server using its Asia/Seoul Clock.
    if (freezeToday.checked) frozenAt.value = frozenAt.max;
}
freezeToday.addEventListener('change', applyToday);
frozenAt.addEventListener('input', () => { freezeToday.checked = false; });
frozenAt.addEventListener('change', () => { freezeToday.checked = false; });
updateFreezerFields();
updateSourceMemo();
applyToday();

const firstInvalidField = document.querySelector('[aria-invalid="true"]:not(:disabled)');
if (firstInvalidField) firstInvalidField.focus();
