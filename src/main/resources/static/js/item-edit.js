(() => {
    const modes = document.getElementById('editModes');
    if (!modes) return;
    const form = modes.closest('form'), byId = id => document.getElementById(id);
    const original = JSON.parse(byId('editOriginalData').textContent);
    const total = Number(modes.dataset.quantity), unit = modes.dataset.unit || '';
    const amount = byId('quantityAmount'), unitInput = byId('quantityUnit'), name = byId('foodName');
    const toggle = byId('editMergeToggle'), picker = byId('editMergePicker');
    const target = byId('mergeTargetId'), version = byId('mergeTargetVersion');
    const results = byId('editMergeResults'), search = byId('editMergeSearch');
    const selected = byId('editMergeSelected'), save = byId('registrationSubmit');
    const categories = [...byId('categoryPanel').querySelectorAll('select,button')];
    const initialDisabled = new Map(categories.map(el => [el, el.disabled]));
    const choices = [...results.querySelectorAll('button')];
    let submitting = false;
    const partial = () => form.querySelector('[name="editMode"]:checked').value === 'partial';
    const read = key => {
        const els = [...form.querySelectorAll(`[name="${key}"]`)];
        if (!els.length) return '';
        if (els[0].type === 'radio') return els.find(el => el.checked)?.value || '';
        if (els[0].type === 'checkbox') return els[0].checked;
        return els[0].value.trim();
    };
    // Validation redisplays contain submitted choices, not the saved category.
    const originalCategory = [
        ['categoryMajorCode', modes.dataset.major || ''],
        ['categoryMinorCode', modes.dataset.minor || '']
    ];
    const equal = (key, a, b) => key === 'quantityAmount' ? Number(a) === Number(b) : String(a ?? '') === String(b ?? '');
    function changed() {
        return Object.keys(original).some(key => {
            if (['category', 'freezeToday'].includes(key)) return false;
            if (partial() && ['quantityAmount','quantityUnit','foodName'].includes(key)) return false;
            if (key === 'sourceMemo' && read('sourceType') !== 'ETC') return false;
            if (['freezeType','frozenAt'].includes(key) && read('storageType') !== 'FREEZER') return false;
            if (key === 'openedAt' && read('openingStatus') !== 'OPENED') return false;
            if (['warningPausedUntil','warningForever'].includes(key) && !read('warningPaused')) return false;
            // The date widget displays a sentinel for "forever"; it is not an edited date.
            if (key === 'warningPausedUntil' && read('warningForever')) return false;
            return !equal(key,read(key),original[key]);
        }) || (!partial() && originalCategory.some(([key,value]) => read(key) !== value));
    }
    function update() {
        const part = partial(), qty = Number(amount.value);
        const valid = /^\d+(\.\d{1,2})?$/.test(amount.value) && qty > 0 && (!part || qty < total);
        const chosen = toggle.checked && choices.find(c => c.dataset.id === target.value);
        picker.hidden = !toggle.checked;
        selected.hidden = !chosen;
        results.hidden = !!chosen;
        search.hidden = !!chosen;
        const query = search.value.trim().toLowerCase();
        choices.forEach(choice => { choice.hidden = !choice.dataset.name.toLowerCase().includes(query); });
        const empty = byId('editMergeEmpty');
        empty.hidden = !toggle.checked || !!chosen || choices.some(choice => !choice.hidden);
        empty.textContent = choices.length ? '검색 결과가 없어. 다른 이름으로 검색해줘.' : '합칠 수 있는 다른 음식이 없어.';
        if (chosen) selected.querySelector('span').textContent = `${chosen.dataset.name} / ${chosen.dataset.category || '-'}`;
        byId('categoryPanel').hidden = !!chosen;
        byId('categoryInherited').hidden = !chosen;
        byId('categoryInheritedHeading').hidden = !chosen;
        if (chosen) byId('categoryInherited').textContent = chosen.dataset.category || '-';
        name.disabled = part || !!chosen;
        name.readOnly = name.disabled;
        unitInput.disabled = part; unitInput.readOnly = part;
        categories.forEach(el => { el.disabled = part || !!chosen || (el.id === 'categoryMinorCode' ? byId('categoryMinorField').hidden : initialDisabled.get(el)); });
        byId('editSharedHelp').hidden = part || toggle.checked;
        byId('editPartialHelp').hidden = !part;
        byId('quantityHelp').hidden = part;
        form.querySelector('label[for="quantityAmount"]').firstChild.textContent = part ? '일부 수량 ' : '수량 ';
        byId('editPartialHelp').classList.toggle('field-error', !valid);
        byId('editPartialHelp').textContent = !valid
            ? `0보다 크고 ${total}${unit}보다 적은 수량을 선택해줘.`
            : `${total}${unit} 중 선택한 ${qty}${unit}만 수정하고 나머지 ${Math.round((total-qty)*100)/100}${unit}는 변경되지 않아.`;
        const missing = toggle.checked && !chosen;
        save.disabled = submitting || !valid || missing || (!chosen && !changed());
        byId('editSaveHint').textContent = missing ? '합칠 음식을 선택하거나 ‘다른 음식과 합치기’를 꺼줘.' : '';
    }
    const originalMajor = modes.dataset.major || '';
    const originalMinor = modes.dataset.minor || '';
    modes.addEventListener('change', () => {
        const nextMode = partial() ? 'partial' : 'all';
        // Flush the warning widget's saved date before restoring the persisted values.
        const forever = form.querySelector('[name="warningForever"]');
        forever.checked = false;
        forever.dispatchEvent(new Event('change', {bubbles:true}));
        form.reset();
        form.querySelector(`[name="editMode"][value="${nextMode}"]`).checked = true;
        for (const [key,value] of Object.entries(original)) {
            form.querySelectorAll(`[name="${key}"]`).forEach(el => {
                if (el.type === 'checkbox') el.checked = !!value;
                else if (el.type === 'radio') el.checked = el.value === value;
                else el.value = value ?? '';
            });
        }
        window.frizerCategoryPicker.restore(originalMajor, originalMinor);
        toggle.checked = false;
        target.value = ''; version.value = ''; search.value = '';
        choices.forEach(choice => { choice.hidden = false; });
        draftName = original.foodName;
        amount.value = nextMode === 'partial' ? '' : String(original.quantityAmount ?? '');
        form.querySelectorAll('[data-edit-mirror]').forEach(el => el.remove());
        // Refresh conditional storage/date controls and the quantity input's typing state.
        for (const key of ['sourceType','storageType','openingStatus','warningForever','freezeToday']) {
            const el = form.querySelector(`[name="${key}"]:checked`) || form.querySelector(`[name="${key}"]`);
            el?.dispatchEvent(new Event('change', {bubbles:true}));
        }
        amount.dispatchEvent(new Event('input', {bubbles:true}));
        update();
    });
    let draftName = name.value;
    choices.forEach(choice => choice.addEventListener('click', () => {
        target.value = choice.dataset.id; version.value = choice.dataset.version;
        draftName = name.value; name.value = choice.dataset.name; update();
    }));
    function clear() {
        // Only replace the name if a selected target actually replaced it first.
        if (target.value) name.value = partial() ? original.foodName : draftName;
        target.value = ''; version.value = ''; update();
    }
    byId('editMergeClear').addEventListener('click', clear);
    toggle.addEventListener('change', () => { if (!toggle.checked) clear(); else update(); });
    form.addEventListener('input', update);
    form.addEventListener('change', update);
    window.addEventListener('pageshow', update);
    form.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        update();
        if (save.disabled) { event.preventDefault(); return; }
        // Disabled controls remain visibly locked; submit the original identity and unit explicitly.
        form.querySelectorAll('[data-edit-mirror]').forEach(el => el.remove());
        // Submit values of locked fields, never commands from disabled buttons.
        [name,unitInput,...categories.filter(el => el.tagName === 'SELECT' && el.name)].filter(el => el.disabled).forEach(el => {
            const hidden = document.createElement('input'); hidden.type = 'hidden'; hidden.name = el.name;
            hidden.value = el === name ? original.foodName : el.value; hidden.dataset.editMirror = ''; form.append(hidden);
        });
        submitting = true;
        save.disabled = true;
    });
    if (target.value) {
        const chosen = choices.find(c => c.dataset.id === target.value);
        if (chosen) name.value = chosen.dataset.name;
    }
    update();
})();
