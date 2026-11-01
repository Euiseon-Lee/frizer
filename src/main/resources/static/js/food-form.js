const storageInputs = document.querySelectorAll('input[name="storageType"]');
const sourceInput = document.getElementById('sourceType');
const sourceMemoField = document.getElementById('sourceMemoField');
const sourceMemo = document.getElementById('sourceMemo');
const freezeToday = document.querySelector('input[name="freezeToday"]');
const frozenAt = document.getElementById('frozenAt');
const freezeType = document.getElementById('freezeType');
const commercialFreezeOption = freezeType.querySelector('option[value="COMMERCIAL_FROZEN"]');
function updateFreezeOptions() {
    const storage = document.querySelector('input[name="storageType"]:checked')?.value;
    const isDelivery = sourceInput.value === 'DELIVERY_LEFTOVER';
    if (isDelivery) {
        freezeType.value = 'HOME_FROZEN';
        commercialFreezeOption.remove();
    } else if (!commercialFreezeOption.isConnected) {
        freezeType.append(commercialFreezeOption);
    }
    document.getElementById('deliveryStorageHelp').hidden = !isDelivery || storage !== undefined;

    document.getElementById('deliveryFreezeHelp').hidden = !isDelivery;
}
function updateSourceMemo() {
    sourceMemoField.hidden = sourceInput.value !== 'ETC';
    sourceMemo.disabled = sourceMemoField.hidden;
}
storageInputs.forEach(input => input.addEventListener('change', updateFreezeOptions));
sourceInput.addEventListener('change', updateFreezeOptions);
sourceInput.addEventListener('change', updateSourceMemo);
function applyToday() {
    // max is rendered by the server using its Asia/Seoul Clock.
    if (freezeToday.checked) frozenAt.value = frozenAt.max;
}
freezeToday.addEventListener('change', applyToday);
frozenAt.addEventListener('input', () => { freezeToday.checked = false; });
frozenAt.addEventListener('change', () => { freezeToday.checked = false; });
updateFreezeOptions();
updateSourceMemo();
applyToday();
