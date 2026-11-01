const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function element(value = '') {
    return {value, hidden: false, checked: false, dataset: {}, textContent: '', listeners: {}, style: {}, children: [],
        addEventListener(type, fn) {(this.listeners[type] ??= []).push(fn)},
        emit(type, event = {}) {for (const fn of this.listeners[type] ?? []) fn(event)},
        dispatchEvent(event) {this.emit(event.type, event)},
        removeAttribute(name) {delete this[name]}, setSelectionRange() {},
        append(child) {
            if (child.parentElement) child.parentElement.children = child.parentElement.children.filter(other => other !== child);
            this.children.push(child); child.parentElement = this;
        }, focus() {this.focused = true},
        replaceChildren(...children) {this.children = children}, add(child) {this.children.push(child)}, selectedIndex: 0,
        classList: {toggle() {}}};
}
function cooking({master = '', amount = '', unit = '', major = '', minor = '', selected = false, query = '', searched = false, version = '7', names = ['감자', '소고기', 'MILK'], foodNames = ['카레', '카레', 'MILK']} = {}) {
    const ids = Object.fromEntries(['cookingForm', 'ingredientSelection', 'ingredientSearch', 'searchIngredients',
        'ingredientSearchStatus', 'ingredientSearchPerformed', 'selectedCookingIngredients', 'cookingIngredientResults', 'masterId',
        'quantityAmount', 'quantityUnit', 'foodName', 'categoryMajorCode', 'categoryMinorCode',
        'cookingCategoryCatalog', 'cookingCategories', 'cookingMinorField', 'cookingCategoryExample',
        'masterVersion', 'cookingNameField', 'cookingResultModes',
        'foodSearch', 'searchFoods', 'foodCandidates', 'foodSearchStatus', 'existingFoodPicker', 'selectedFoodSummary',
        'cookingFoodSearchField', 'selectedFoodName', 'selectedFoodCategory', 'cookingSubmit', 'changeSelectedFood'].map(id => [id, element()]));
    const rows = names.map((name, index) => {
        const row = element(); row.dataset.ingredientName = name;
        row.height = 80; row.getBoundingClientRect = () => ({height: row.height});
        row.checkbox = element(); row.amount = element(); row.amountField = element();
        row.foodId = element(String(index + 1)); row.version = element('0');
        row.fields = [row.foodId, row.version, row.checkbox, row.amount];
        row.fields.forEach((field, i) => {
            field.dataset.ingredientField = ['foodId', 'version', 'selected', 'amount'][i];
            field.type = ['hidden', 'hidden', 'checkbox', 'number'][i];
            field.name = `ingredients[${index}].${field.dataset.ingredientField}`;
        });
        row.querySelectorAll = () => row.fields;
        row.querySelector = selector => ({'[type=checkbox]': row.checkbox, '[type=number]': row.amount,
            '.cooking-amount-field': row.amountField})[selector];
        return row;
    });
    rows[0].checkbox.checked = selected; rows[0].amount.value = selected ? '2' : '';
    ids.ingredientSearch.value = query;
    ids.ingredientSearchPerformed.value = String(searched);
    ids.quantityAmount.value = amount; ids.quantityUnit.value = unit;
    ids.categoryMajorCode.value = major; ids.categoryMinorCode.value = minor;
    ids.cookingCategoryCatalog.textContent = JSON.stringify([
        {code: 'meat', requiresMinor: true, minors: [{code: 'meat_beef', label: '소고기', example: '등심'}]},
        {code: 'kimchi', requiresMinor: false, minors: []}
    ]);
    ids.searchIngredients.value = 'ingredients';
    ids.cookingForm.querySelectorAll = () => rows;
    const candidates = foodNames.map((name, index) => {
        const button = element();
        button.height = 80; button.getBoundingClientRect = () => ({height: button.height});
        button.dataset = {masterId: String(index + 1), name, unit: index === 0 ? '인분' : '통', version: '7', major: index === 1 ? '' : 'kimchi'};
        button.querySelector = () => ({textContent: `분류 / #${index + 1}`});
        ids.foodCandidates.append(button);
        return button;
    });
    ids.foodCandidates.rowGap = '8px';
    ids.foodCandidates.querySelectorAll = () => candidates;
    ids.masterId.value = master; ids.masterVersion.value = master ? version : '';
    let mode = master ? 'existing' : 'new';
    const radios = [element('new'), element('existing')];
    ids.cookingResultModes.querySelector = () => ({value: mode});
    ids.cookingResultModes.querySelectorAll = () => radios;
    const window = element();
    const context = vm.createContext({document: {getElementById: id => ids[id]}, window,
        getComputedStyle: element => ({rowGap: element.rowGap ?? '0px'}),
        Option: function(text, value) {this.value = value}, Event: function(type) {this.type = type}});
    for (const file of ['quantity-input', 'cooking-form']) vm.runInContext(fs.readFileSync(`src/main/resources/static/js/${file}.js`, 'utf8'), context);
    function changeMode(value) {mode = value; radios.find(radio => radio.value === value).emit('change')}
    return {ids, rows, candidates, mode: changeMode, resize() {window.emit('resize')},
        search(value) {ids.ingredientSearch.value = value; ids.ingredientSearch.emit('input'); ids.searchIngredients.emit('click', {preventDefault() {}})},
        searchFoods(value) {ids.foodSearch.value = value; ids.foodSearch.emit('input'); ids.searchFoods.emit('click')},
        select(index, checked = true) {rows[index].checkbox.checked = checked; rows[index].checkbox.emit('change')},
        master(value) {if (!value) changeMode('new'); else {changeMode('existing'); candidates.find(button => button.dataset.masterId === value).emit('click')}}};
}

test('cooking: initial inventory is hidden, explicit search ignores case and changing query hides old matches', () => {
    const e = cooking();
    assert.ok(e.rows.every(row => row.hidden));
    assert.equal(e.ids.searchIngredients.hidden, false); assert.equal(e.ids.ingredientSelection.hidden, true);
    e.ids.ingredientSearch.value = '감자'; e.ids.ingredientSearch.emit('input');
    assert.ok(e.rows.every(row => row.hidden));
    e.search('milk'); assert.deepEqual(e.rows.map(row => row.hidden), [true, true, false]);
    e.ids.ingredientSearch.emit('input'); assert.ok(e.rows.every(row => row.hidden));
    e.search('없는 재료'); assert.equal(e.ids.cookingIngredientResults.hidden, true);
    e.search('  '); assert.equal(e.ids.ingredientSearchStatus.hidden, false);
    assert.ok(e.rows.every(row => !row.hidden));
});

test('cooking: blank search lists all ingredients while keeping selection and amounts through filtering and redisplay', () => {
    const e = cooking(); e.search('');
    assert.ok(e.rows.every(row => !row.hidden));
    assert.equal(e.ids.ingredientSearchPerformed.value, 'true');
    e.select(0); e.rows[0].amount.value = '1.5'; e.select(1);
    assert.equal(e.ids.ingredientSelection.textContent, '재료 2개 선택');
    e.search('없는 재료'); assert.equal(e.rows[0].hidden, false); assert.equal(e.rows[1].hidden, false);
    e.search('  '); assert.ok(e.rows.every(row => !row.hidden));
    assert.equal(e.rows[0].amount.value, '1.5');
    assert.equal(e.rows[0].parentElement, e.ids.selectedCookingIngredients);
    assert.equal(e.rows[2].parentElement, e.ids.cookingIngredientResults);
    e.select(1, false); assert.equal(e.ids.ingredientSelection.textContent, '재료 1개 선택');
    e.ids.ingredientSearch.emit('input'); assert.equal(e.ids.ingredientSearchPerformed.value, 'false');
    assert.deepEqual(e.rows.map(row => row.hidden), [false, true, true]);
    const restored = cooking({selected: true, searched: true});
    assert.ok(restored.rows.every(row => !row.hidden));
    assert.equal(restored.rows[0].amount.value, '2');
    assert.equal(restored.rows[1].amount.disabled, true);
});

test('cooking: result viewport fits ten rows and shrinks for fewer matches or selected rows', () => {
    const e = cooking({names: Array.from({length: 12}, (_, i) => `재료 ${i + 1}`)});
    const results = e.ids.cookingIngredientResults;
    e.search(''); assert.equal(results.style.maxHeight, '800px'); assert.equal(results.tabIndex, 0);
    e.rows[0].height = 120; e.resize(); assert.equal(results.style.maxHeight, '840px');
    results.scrollTop = 300; e.search('재료 1');
    assert.equal(results.style.maxHeight, ''); assert.equal(results.tabIndex, -1); assert.equal(results.scrollTop, 0);
    e.search(''); e.select(0); assert.equal(results.style.maxHeight, '800px');
    e.select(0, false); assert.equal(results.style.maxHeight, '800px');
    e.select(0);
    e.select(1); assert.equal(results.style.maxHeight, '');
    e.select(2); assert.equal(results.style.maxHeight, '');
    e.search('없음'); assert.equal(results.hidden, true); assert.equal(results.style.maxHeight, '');
});

test('cooking: selected ingredients and amounts stay visible across searches; only selected amounts submit', () => {
    const e = cooking(); e.search('감자'); e.select(0); e.rows[0].amount.value = '2'; e.search('소고기');
    assert.equal(e.rows[0].parentElement, e.ids.selectedCookingIngredients);
    assert.equal(e.rows[0].hidden, false); assert.equal(e.rows[0].amount.value, '2');
    assert.equal(e.rows[0].amount.disabled, false); assert.equal(e.rows[0].amount.required, true);
    assert.equal(e.rows[1].amount.disabled, true); assert.equal(e.rows[1].amountField.hidden, true);
    e.select(0, false); assert.equal(e.rows[0].hidden, true); assert.equal(e.rows[0].amount.disabled, true);
});

test('cooking: Korean composition does not search or submit; Enter runs search afterwards', () => {
    const e = cooking(); e.ids.ingredientSearch.value = '감자'; let prevented = 0;
    for (const event of [{isComposing: true}, {keyCode: 229}]) {
        e.ids.ingredientSearch.emit('keydown', {key: 'Enter', preventDefault() {prevented++}, ...event});
    }
    assert.equal(prevented, 2); assert.ok(e.rows.every(row => row.hidden));
    e.ids.ingredientSearch.emit('keydown', {key: 'Enter', preventDefault() {}});
    assert.equal(e.rows[0].hidden, false);
});

test('cooking: existing selection suggests only its unit, returning to new restores empty fields', () => {
    const e = cooking(); assert.equal(e.ids.quantityAmount.value, ''); assert.equal(e.ids.quantityUnit.value, '');
    e.master('1'); assert.equal(e.ids.quantityUnit.value, '인분'); assert.equal(e.ids.quantityAmount.value, '');
    e.master(''); assert.equal(e.ids.quantityUnit.value, ''); assert.equal(e.ids.quantityAmount.value, '');
});

test('cooking: switching results preserves each manually entered quantity/unit, including intentionally cleared unit', () => {
    const e = cooking(); e.ids.quantityAmount.value = '2'; e.ids.quantityUnit.value = '접시';
    e.master('1'); e.ids.quantityAmount.value = '4'; e.ids.quantityUnit.value = '';
    e.master('2'); assert.equal(e.ids.quantityUnit.value, '통');
    e.master(''); assert.equal(e.ids.quantityAmount.value, '2'); assert.equal(e.ids.quantityUnit.value, '접시');
    e.master('1'); assert.equal(e.ids.quantityAmount.value, '4'); assert.equal(e.ids.quantityUnit.value, '');
});

test('cooking: redisplay preserves posted amounts and units with selected ingredients visible without a search', () => {
    const e = cooking({master: '1', amount: '3.5', unit: '접시', selected: true, version: '3'});
    assert.equal(e.ids.quantityAmount.value, '3.5'); assert.equal(e.ids.quantityUnit.value, '접시');
    assert.equal(e.ids.masterVersion.value, '3'); assert.equal(e.ids.selectedFoodSummary.hidden, false);
    assert.equal(e.rows[0].hidden, false); assert.equal(e.rows[0].amount.value, '2');
    assert.ok(e.rows.slice(1).every(row => row.hidden));
});

test('cooking: existing results require an explicit search, distinguish duplicate names and clear stale matches', () => {
    const e = cooking(); e.mode('existing'); assert.equal(e.ids.foodCandidates.hidden, true);
    e.ids.foodSearch.value = '카레'; e.ids.foodSearch.emit('input'); assert.equal(e.ids.foodCandidates.hidden, true);
    e.searchFoods('카레'); assert.deepEqual(e.candidates.map(button => button.hidden), [false, false, true]);
    e.searchFoods('milk'); assert.deepEqual(e.candidates.map(button => button.hidden), [true, true, false]);
    e.ids.foodSearch.emit('input'); assert.equal(e.ids.foodCandidates.hidden, true);
    e.searchFoods('없는 음식'); assert.match(e.ids.foodSearchStatus.textContent, /일치하는 음식이 없어/);
    e.searchFoods('  '); assert.equal(e.ids.foodSearchStatus.hidden, false);
    assert.ok(e.candidates.every(button => !button.hidden));
});

test('cooking: blank existing-food search fits ten cards including gaps and preserves selection drafts', () => {
    const e = cooking({foodNames: Array.from({length: 12}, (_, i) => `음식 ${i + 1}`)});
    const list = e.ids.foodCandidates;
    e.mode('existing'); assert.equal(list.hidden, true);
    e.searchFoods(''); assert.equal(list.hidden, false); assert.equal(list.style.maxHeight, '872px');
    assert.ok(e.candidates.every(button => !button.hidden));
    e.candidates[0].height = 120; e.resize(); assert.equal(list.style.maxHeight, '912px');
    list.scrollTop = 300; e.searchFoods('음식 1');
    assert.equal(list.style.maxHeight, ''); assert.equal(list.scrollTop, 0);
    assert.equal(e.candidates.filter(button => !button.hidden).length, 4);
    e.searchFoods('  '); assert.equal(list.style.maxHeight, '912px');
    e.master('1'); assert.equal(list.hidden, true); assert.equal(list.style.maxHeight, '');
    e.ids.quantityAmount.value = '2.5'; e.ids.quantityUnit.value = '접시';
    e.ids.changeSelectedFood.emit('click'); assert.equal(list.hidden, true);
    e.searchFoods(''); e.master('1');
    assert.equal(e.ids.quantityAmount.value, '2.5'); assert.equal(e.ids.quantityUnit.value, '접시');
    const ten = cooking({foodNames: Array.from({length: 10}, (_, i) => `음식 ${i}`)});
    ten.mode('existing'); ten.searchFoods(''); assert.equal(ten.ids.foodCandidates.style.maxHeight, '');
});

test('cooking: selection summary can be changed; unselected existing mode blocks submission but allows ingredient search', () => {
    const e = cooking(); e.master('1'); assert.equal(e.ids.cookingFoodSearchField.hidden, true);
    assert.equal(e.ids.selectedFoodName.textContent, '카레'); assert.equal(e.ids.foodName.disabled, true);
    e.ids.changeSelectedFood.emit('click'); assert.equal(e.ids.selectedFoodSummary.hidden, true);
    assert.equal(e.ids.masterId.value, ''); assert.equal(e.ids.masterVersion.value, '');
    assert.equal(e.ids.cookingSubmit.disabled, true);
    let prevented = 0;
    e.ids.cookingForm.emit('submit', {preventDefault() {prevented++}});
    e.ids.cookingForm.emit('submit', {submitter: {name: 'refresh'}, preventDefault() {prevented++}});
    assert.equal(prevented, 1);
    e.master('2'); assert.equal(e.ids.cookingSubmit.disabled, false); assert.equal(e.ids.cookingCategories.hidden, false);
    e.mode('new'); assert.equal(e.ids.masterId.disabled, true); assert.equal(e.ids.foodName.disabled, false);
});

test('cooking: result search Enter respects Korean composition and never submits the cooking form', () => {
    const e = cooking(); e.mode('existing'); e.ids.foodSearch.value = '카레'; let prevented = 0;
    for (const extra of [{isComposing: true}, {keyCode: 229}]) {
        e.ids.foodSearch.emit('keydown', {key: 'Enter', ...extra, preventDefault() {prevented++}});
    }
    assert.equal(prevented, 2); assert.equal(e.ids.foodCandidates.hidden, true);
    e.ids.foodSearch.emit('keydown', {key: 'Enter', preventDefault() {prevented++}});
    assert.equal(prevented, 3); assert.equal(e.ids.foodCandidates.hidden, false);
});

test('cooking: shared quantity input restores the current draft after invalid typing, not a previous result', () => {
    const e = cooking(); e.ids.quantityAmount.value = '2.5'; e.ids.quantityAmount.emit('input'); e.master('1');
    e.ids.quantityAmount.value = 'x'; e.ids.quantityAmount.emit('input'); assert.equal(e.ids.quantityAmount.value, '');
    e.mode('new'); e.ids.quantityAmount.value = 'x'; e.ids.quantityAmount.emit('input'); assert.equal(e.ids.quantityAmount.value, '2.5');
});

test('cooking: inventory beyond the binder limit submits only selected rows with dense indexes', () => {
    const e = cooking({names: Array.from({length: 300}, (_, i) => `재료 ${i}`)});
    assert.ok(e.rows.every(row => row.fields.every(input => !input.name)));
    e.select(256); e.rows[256].amount.value = '1.5';
    assert.deepEqual(e.rows[256].fields.map(input => input.name),
        ['foodId', 'version', 'selected', 'amount'].map(field => `ingredients[0].${field}`));
    assert.ok(e.rows[256].fields.every(input => !input.disabled));
    e.select(299); e.select(0);
    assert.equal(e.rows[299].foodId.name, 'ingredients[2].foodId');
    e.select(0, false);
    assert.equal(e.rows[256].foodId.name, 'ingredients[0].foodId');
    assert.equal(e.rows[299].foodId.name, 'ingredients[1].foodId');
    assert.ok(e.rows[0].fields.every(input => !input.name));
    assert.equal(e.rows[0].checkbox.disabled, undefined);
    assert.equal(e.rows[0].foodId.disabled, true);
    // Browser-restored checkbox state is normalized again when submitting.
    e.rows[256].checkbox.checked = false;
    e.ids.cookingForm.emit('submit', {preventDefault() {}});
    assert.equal(e.rows[299].foodId.name, 'ingredients[0].foodId');
    assert.ok(e.rows[256].fields.every(input => !input.name));
    assert.equal(e.rows[256].amount.value, '1.5');
});

test('cooking: each result keeps its own category draft without assigning it to a different unclassified food', () => {
    const e = cooking(); e.candidates[2].dataset.major = '';
    e.ids.categoryMajorCode.value = 'kimchi'; e.ids.categoryMajorCode.emit('change');
    e.master('2'); assert.equal(e.ids.categoryMajorCode.value, '');
    e.ids.categoryMajorCode.value = 'meat'; e.ids.categoryMajorCode.emit('change');
    e.ids.categoryMinorCode.value = 'meat_beef'; e.ids.categoryMinorCode.emit('change');
    e.ids.changeSelectedFood.emit('click'); e.master('3');
    assert.equal(e.ids.categoryMajorCode.value, ''); assert.equal(e.ids.categoryMinorCode.value, '');
    assert.equal(e.ids.categoryMajorCode.required, true);
    e.master('2'); assert.equal(e.ids.categoryMajorCode.value, 'meat');
    assert.equal(e.ids.categoryMinorCode.value, 'meat_beef'); assert.equal(e.ids.categoryMinorCode.required, true);
    e.master('1'); assert.equal(e.ids.categoryMajorCode.disabled, true);
    e.master(''); assert.equal(e.ids.categoryMajorCode.value, 'kimchi');
    e.master('2'); assert.equal(e.ids.categoryMinorCode.value, 'meat_beef');
    e.ids.categoryMajorCode.value = 'kimchi'; e.ids.categoryMajorCode.emit('change');
    assert.equal(e.ids.categoryMinorCode.value, ''); assert.equal(e.ids.categoryMinorCode.disabled, true);
});

test('cooking: category returned by server validation survives initialization and result switching', () => {
    const e = cooking({master: '2', major: 'meat', minor: 'meat_beef'});
    assert.equal(e.ids.categoryMajorCode.value, 'meat'); assert.equal(e.ids.categoryMinorCode.value, 'meat_beef');
    e.master(''); assert.equal(e.ids.categoryMajorCode.value, '');
    e.master('2'); assert.equal(e.ids.categoryMajorCode.value, 'meat'); assert.equal(e.ids.categoryMinorCode.value, 'meat_beef');
});
