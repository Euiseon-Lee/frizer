(() => {
    const picker = document.getElementById('registrationPicker');
    if (!picker) return;
    const masterId = document.getElementById('masterId');
    const version = document.getElementById('masterVersion');
    const name = document.getElementById('foodName');
    const category = document.getElementById('category');
    const unit = document.getElementById('quantityUnit');
    const search = document.getElementById('foodSearch');
    const candidates = [...document.querySelectorAll('#foodCandidates button')];
    const summary = document.getElementById('selectedFoodSummary');
    const mode = () => picker.querySelector('[name="registrationMode"]:checked').value;
    let newName = mode() === 'new' ? name.value : '';
    let newCategory = mode() === 'new' ? category.value : '';
    let selected = candidates.find(button => button.dataset.masterId === masterId.value);
    let searchedQuery = null;
    let submitting = false;
    const results = document.getElementById('foodCandidates');
    const searchStatus = document.getElementById('foodSearchStatus');

    function filterCandidates() {
        candidates.forEach(button => {
            button.hidden = !searchedQuery || !button.dataset.name.toLocaleLowerCase().includes(searchedQuery);
        });
        const count = candidates.filter(button => !button.hidden).length;
        results.hidden = !!selected || !searchedQuery || count === 0;
        searchStatus.hidden = !!selected || !searchedQuery;
        searchStatus.textContent = searchedQuery
            ? (count ? `검색 결과 ${count}건` : '일치하는 음식이 없어. 다른 이름으로 검색하거나 신규 등록해줘.')
            : '';
    }
    function searchFoods() {
        searchedQuery = search.value.trim().toLocaleLowerCase() || null;
        filterCandidates();
        if (!searchedQuery) search.focus();
    }
    function render() {
        const bulk = mode() === 'bulk';
        document.getElementById('singleRegistrationFields').hidden = bulk;
        document.getElementById('bulkRegistrationPanel').hidden = !bulk;
        const existing = mode() === 'existing';
        document.getElementById('existingFoodPicker').hidden = !existing;
        document.getElementById('foodNameField').hidden = existing;
        name.readOnly = existing;
        category.readOnly = existing;
        name.disabled = existing;
        category.disabled = existing;
        document.getElementById('sharedCategoryHelp').hidden = !existing;
        masterId.disabled = version.disabled = !existing;
        name.value = existing ? (selected?.dataset.name || '') : newName;
        category.value = existing ? (selected?.dataset.category || '') : newCategory;
        category.closest('.field').hidden = existing && (!selected || !category.value.trim());
        summary.hidden = !selected;
        search.closest('.field').hidden = !!selected;
        if (selected) {
            document.getElementById('selectedFoodName').textContent = selected.dataset.name;
            document.getElementById('selectedFoodCategory').textContent = selected.querySelector('span').textContent;
        }
        const submit = document.getElementById('registrationSubmit');
        submit.textContent = submitting ? '등록하는 중…' : '등록 완료';
        submit.disabled = submitting || (existing && !selected);
        document.getElementById('registrationCancel').href = existing && selected
            ? selected.dataset.detailUrl : picker.dataset.listUrl;
        filterCandidates();
    }
    picker.querySelectorAll('[name="registrationMode"]').forEach(input => input.addEventListener('change', render));
    name.addEventListener('input', () => { if (mode() === 'new') newName = name.value; });
    category.addEventListener('input', () => { if (mode() === 'new') newCategory = category.value; });
    candidates.forEach(button => button.addEventListener('click', () => {
        selected = button;
        masterId.value = button.dataset.masterId;
        version.value = button.dataset.version;
        if (button.dataset.unit) unit.value = button.dataset.unit;
        render();
        document.getElementById('quantityAmount').focus();
    }));
    document.getElementById('changeSelectedFood').addEventListener('click', () => {
        selected = null;
        masterId.value = version.value = '';
        searchedQuery = null;
        search.value = '';
        render();
        search.focus();
    });
    document.getElementById('searchFoods').addEventListener('click', searchFoods);
    search.addEventListener('keydown', event => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        if (!event.isComposing && event.keyCode !== 229) searchFoods();
    });
    search.addEventListener('input', () => {
        searchedQuery = null;
        filterCandidates();
    });
    if (selected && !unit.value && selected.dataset.unit) unit.value = selected.dataset.unit;
    picker.closest('form').addEventListener('submit', event => {
        if (mode() === 'bulk' || submitting || (mode() === 'existing' && !selected)) {
            event.preventDefault();
            return;
        }
        submitting = true;
        // Keep input controls enabled so their values are included in the native POST.
        const submit = document.getElementById('registrationSubmit');
        submit.disabled = true;
        submit.textContent = '등록하는 중…';
    });
    window.addEventListener('pageshow', () => {
        submitting = false;
        // Browsers can restore form values after script initialization without input events.
        if (mode() === 'new') {
            newName = name.value;
            newCategory = category.value;
        }
        render();
    });
    // Preserve the submitted version on validation errors: never silently accept a changed master.
    render();
})();