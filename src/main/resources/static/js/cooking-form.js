(() => {
    const form = document.getElementById('cookingForm');
    if (!form) return;
    const rows = [...form.querySelectorAll('[data-ingredient-name]')];
    const selection = document.getElementById('ingredientSelection');
    const search = document.getElementById('ingredientSearch');
    const searchPerformed = document.getElementById('ingredientSearchPerformed');
    const searchStatus = document.getElementById('ingredientSearchStatus');
    const selectedIngredients = document.getElementById('selectedCookingIngredients');
    const results = document.getElementById('cookingIngredientResults');
    let query = search?.value.trim().toLocaleLowerCase() || '';
    let searched = searchPerformed?.value === 'true' || !!query;
    function resizeResults(list) {
        const visible = [...list.children].filter(row => !row.hidden);
        const scrollable = !list.hidden && visible.length > 10;
        list.classList.toggle('is-scrollable', scrollable);
        list.tabIndex = scrollable ? 0 : -1;
        const gap = parseFloat(getComputedStyle(list).rowGap) || 0;
        list.style.maxHeight = scrollable
            ? `${Math.ceil(visible.slice(0, 10).reduce((height, row) => height + row.getBoundingClientRect().height, 0) + gap * 9)}px`
            : '';
    }
    function selectedRows() {
        let count = 0;
        let matches = 0;
        for (const row of rows) {
            const selected = row.querySelector('[type=checkbox]').checked;
            // Inventory rows are display-only until selected. Keep submitted indexes dense.
            for (const input of row.querySelectorAll('[data-ingredient-field]')) {
                if (selected) input.name = `ingredients[${count}].${input.dataset.ingredientField}`;
                else input.removeAttribute('name');
                if (input.type !== 'checkbox') input.disabled = !selected;
            }
            row.querySelector('[type=number]').required = selected;
            row.querySelector('[type=number]').disabled = !selected;
            row.querySelector('.cooking-amount-field').hidden = !selected;
            row.classList.toggle('is-selected', selected);
            const container = selected ? selectedIngredients : results;
            if (row.parentElement !== container) container.append(row);
            const matchesQuery = searched && row.dataset.ingredientName.toLocaleLowerCase().includes(query);
            row.hidden = !selected && !matchesQuery;
            if (!selected && matchesQuery) matches++;
            if (selected) count++;
        }
        selection.textContent = count ? `재료 ${count}개 선택` : '';
        selection.hidden = !count;
        searchStatus.hidden = !searched;
        searchStatus.textContent = matches ? `검색 결과 ${matches}개` : '추가로 선택할 재료가 없어.';
        results.hidden = !matches;
        resizeResults(results);
    }
    for (const row of rows) row.querySelector('[type=checkbox]').addEventListener('change', selectedRows);
    function searchIngredients(event) {
        event.preventDefault();
        query = search.value.trim().toLocaleLowerCase();
        searched = true;
        searchPerformed.value = 'true';
        selectedRows();
        results.scrollTop = 0;
    }
    document.getElementById('searchIngredients')?.addEventListener('click', searchIngredients);
    search?.addEventListener('keydown', event => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        if (!event.isComposing && event.keyCode !== 229) searchIngredients(event);
    });
    search?.addEventListener('input', () => {
        query = '';
        searched = false;
        searchPerformed.value = 'false';
        selectedRows();
    });
    const master = document.getElementById('masterId');
    const version = document.getElementById('masterVersion');
    const modes = document.getElementById('cookingResultModes');
    const mode = () => modes.querySelector('[name=resultMode]:checked').value;
    const foodSearch = document.getElementById('foodSearch');
    const candidates = [...document.getElementById('foodCandidates').querySelectorAll('button')];
    const foodResults = document.getElementById('foodCandidates');
    const foodStatus = document.getElementById('foodSearchStatus');
    let selected = candidates.find(button => button.dataset.masterId === master.value);
    let foodQuery = null;
    const amount = document.getElementById('quantityAmount');
    const unit = document.getElementById('quantityUnit');
    const resultKey = () => mode() === 'new' ? '' : selected?.dataset.masterId || 'unselected';
    let currentResult = resultKey();
    const resultDrafts = new Map();
    function switchResult() {
        const next = resultKey();
        if (next === currentResult) return;
        resultDrafts.set(currentResult, {amount: amount.value, unit: unit.value, major: major.value, minor: minor.value});
        currentResult = next;
        const draft = resultDrafts.get(next);
        amount.value = draft?.amount ?? '';
        unit.value = draft?.unit ?? (mode() === 'existing' ? selected?.dataset.unit || '' : '');
        major.value = draft?.major ?? '';
        category(draft?.minor ?? '');
        // Keep the shared quantity input's decimal-entry state in sync with restored drafts.
        amount.dispatchEvent(new Event('input', {bubbles: true}));
    }
    const name = document.getElementById('foodName');
    const major = document.getElementById('categoryMajorCode');
    const minor = document.getElementById('categoryMinorCode');
    const catalog = JSON.parse(document.getElementById('cookingCategoryCatalog').textContent);
    function category(value = minor.value) {
        const choice = catalog.find(c => c.code === major.value);
        minor.replaceChildren(new Option('중분류를 골라줘', ''));
        for (const row of choice?.minors || []) minor.add(new Option(row.label, row.code));
        minor.value = value;
        if (minor.selectedIndex < 0) minor.selectedIndex = 0;
        const inherited = mode() === 'existing' && !!selected?.dataset.major;
        const pending = mode() === 'existing' && !selected;
        document.getElementById('cookingCategories').hidden = inherited || pending;
        document.getElementById('cookingMinorField').hidden = !choice?.requiresMinor;
        major.disabled = inherited || pending;
        major.required = !major.disabled;
        minor.disabled = inherited || pending || !choice?.requiresMinor;
        minor.required = !minor.disabled;
        const child = choice?.minors.find(c => c.code === minor.value);
        const example = child?.example || (!choice?.requiresMinor ? choice?.example : '');
        document.getElementById('cookingCategoryExample').textContent = example ? `예) ${example.replaceAll('·', ', ')}` : '';
    }
    function filterFoods() {
        const searched = foodQuery !== null;
        for (const button of candidates) button.hidden = !searched || !button.dataset.name.toLocaleLowerCase().includes(foodQuery);
        const count = candidates.filter(button => !button.hidden).length;
        foodResults.hidden = mode() !== 'existing' || !!selected || !searched || !count;
        foodStatus.hidden = mode() !== 'existing' || !!selected || !searched;
        foodStatus.textContent = searched ? (count ? `검색 결과 ${count}건` : '일치하는 음식이 없어. 다른 이름으로 검색하거나 새로운 음식으로 등록해줘.') : '';
        resizeResults(foodResults);
    }
    function result() {
        const existing = mode() === 'existing';
        document.getElementById('existingFoodPicker').hidden = !existing;
        document.getElementById('cookingNameField').hidden = existing;
        name.required = !existing;
        name.disabled = existing;
        master.disabled = version.disabled = !existing;
        document.getElementById('selectedFoodSummary').hidden = !selected;
        document.getElementById('cookingFoodSearchField').hidden = !!selected;
        if (selected) {
            document.getElementById('selectedFoodName').textContent = selected.dataset.name;
            document.getElementById('selectedFoodCategory').textContent = selected.querySelector('span').textContent;
        }
        document.getElementById('cookingSubmit').disabled = !rows.length || (existing && !selected);
        category();
        filterFoods();
    }
    for (const radio of modes.querySelectorAll('[name=resultMode]')) radio.addEventListener('change', () => {
        switchResult(); result();
    });
    for (const button of candidates) button.addEventListener('click', () => {
        selected = button;
        master.value = button.dataset.masterId;
        version.value = button.dataset.version;
        switchResult(); result(); amount.focus();
    });
    document.getElementById('changeSelectedFood').addEventListener('click', () => {
        selected = null;
        master.value = version.value = '';
        foodQuery = null;
        foodSearch.value = '';
        switchResult(); result(); foodSearch.focus();
    });
    function searchFoods() {
        foodQuery = foodSearch.value.trim().toLocaleLowerCase();
        filterFoods();
        foodResults.scrollTop = 0;
    }
    document.getElementById('searchFoods').addEventListener('click', searchFoods);
    foodSearch.addEventListener('input', () => {foodQuery = null; filterFoods();});
    foodSearch.addEventListener('keydown', event => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        if (!event.isComposing && event.keyCode !== 229) searchFoods();
    });
    form.addEventListener('submit', event => {
        selectedRows();
        if (event.submitter?.name === 'refresh') return;
        if (mode() === 'existing' && !selected) {event.preventDefault(); foodSearch.focus();}
    });
    major.addEventListener('change', () => category(''));
    minor.addEventListener('change', () => category());
    window.addEventListener('resize', () => {resizeResults(results); resizeResults(foodResults);});
    selectedRows(); result();
})();
