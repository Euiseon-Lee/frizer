(() => {
    const root = document.getElementById('categoryPicker');
    if (!root) return;
    const form = root.closest('form');
    const catalog = JSON.parse(document.getElementById('foodCategoryCatalog').textContent);
    const major = document.getElementById('categoryMajorCode');
    const minor = document.getElementById('categoryMinorCode');
    const panel = document.getElementById('categoryPanel');
    const example = document.getElementById('categoryExample');
    const help = document.getElementById('sharedCategoryHelp');
    const inheritedText = document.getElementById('categoryInherited');
    const majorError = document.getElementById('categoryMajorError');
    const minorError = document.getElementById('categoryMinorError');
    const states = new Map();
    const empty = () => ({major: '', minor: ''});
    let mode = document.querySelector('[name="registrationMode"]:checked')?.value || 'edit';
    let key = mode === 'existing' ? `existing:${document.getElementById('masterId').value}` : mode;
    let state = {major: major.value, minor: minor.value};
    states.set(key, state);
    let inherited = root.dataset.inherited === 'true';
    let active = true;
    let submitting = false;
    const choice = () => catalog.find(c => c.code === state.major);
    const exampleText = entry => '예) ' + [...new Set(entry.example.replace(/\s+등$/, '')
        .split(/[·,]/).map(value => value.trim()).filter(Boolean))].join(', ') + ' 등';
    function clearErrors() {
        for (const [input, error] of [[major, majorError], [minor, minorError]]) {
            input.classList.remove('invalid'); input.setAttribute('aria-invalid', 'false');
            error.textContent = ''; error.hidden = true;
        }
    }
    function selectOptions(select, rows, value, placeholder) {
        select.replaceChildren();
        const first = new Option(placeholder, ''); first.disabled = true; select.add(first);
        for (const row of rows) select.add(new Option(row.label, row.code));
        if (value && !rows.some(row => row.code === value)) select.add(new Option('다시 선택해줘', value));
        select.value = value;
        if (!value) select.selectedIndex = 0;
    }
    function render() {
        const parent = choice();
        const needsMinor = !parent || parent.requiresMinor;
        selectOptions(minor, parent?.minors || [], state.minor, '중분류를 골라줘');
        major.value = state.major;
        // Browser restoration or an invalid server value must never choose the first real option.
        if (state.major && major.value !== state.major) { major.add(new Option('다시 선택해줘', state.major)); major.value = state.major; }
        root.hidden = !active || (inherited && root.dataset.hideInherited === 'true');
        inheritedText.hidden = !active || !inherited;
        document.getElementById('categoryInheritedHeading').hidden = !active || !inherited;
        panel.hidden = !active || inherited;
        major.disabled = !active || inherited;
        minor.disabled = !active || inherited || !needsMinor;
        minor.required = active && !inherited && needsMinor;
        major.required = active && !inherited;
        document.getElementById('categoryMinorField').hidden = !needsMinor;
        const child = parent?.minors.find(c => c.code === state.minor);
        example.textContent = child ? exampleText(child)
            : parent && !parent.requiresMinor ? exampleText(parent) : '';
        example.hidden = !example.textContent;
        help.hidden = mode !== 'existing' || !active;
        help.textContent = inherited ? '기존 음식의 분류를 그대로 사용할 거야.'
            : '선택한 분류는 등록할 때 같은 음식의 다른 항목에도 함께 적용돼.';
    }
    function validate() {
        const parent = choice();
        let input, error, text;
        if (!parent) [input, error, text] = [major, majorError, '대분류를 선택해줘.'];
        else if (parent.requiresMinor && !parent.minors.some(c => c.code === state.minor))
            [input, error, text] = [minor, minorError, '중분류를 선택해줘.'];
        if (!input) return true;
        render(); error.textContent = text; error.hidden = false;
        input.classList.add('invalid'); input.setAttribute('aria-invalid', 'true'); input.focus(); return false;
    }
    major.addEventListener('change', () => { state.major = major.value; state.minor = ''; clearErrors(); render(); });
    minor.addEventListener('change', () => { state.minor = minor.value; clearErrors(); render(); });
    window.frizerCategoryPicker = {
        isValid() {
            const parent=choice();
            return active && (inherited || (!!parent && (!parent.requiresMinor || parent.minors.some(c=>c.code===state.minor))));
        },
        restore(majorValue,minorValue) {
            state.major=majorValue || ''; state.minor=minorValue || ''; render();
        },
        switchContext(nextMode, selected, reset = false) {
            const nextKey = nextMode === 'existing' ? `existing:${selected?.dataset.masterId || ''}` : nextMode;
            if (reset) states.delete(nextKey);
            if (nextKey !== key || reset) {
                if (nextMode !== 'bulk') {
                    state = states.get(nextKey) || empty(); states.set(nextKey, state);
                }
                key = nextKey; clearErrors();
            }
            mode = nextMode; active = nextMode !== 'bulk' && (nextMode !== 'existing' || !!selected);
            inherited = nextMode === 'existing' && !!selected?.dataset.major;
            if (nextMode === 'existing') {
                inheritedText.textContent = selected?.dataset.category || '';
            }
            render();
        }
    };
    const refreshButton = root.querySelector('.category-refresh');
    refreshButton.hidden = true;
    refreshButton.type = 'button'; // Hidden submit buttons still receive implicit Enter submissions.
    form.addEventListener('submit', event => {
        if (event.submitter?.value === 'refresh') return;
        if (submitting) { event.preventDefault(); return; }
        if (!active) return;
        if (!inherited) {
            clearErrors(); if (!validate()) { event.preventDefault(); return; }
        }
        if (mode === 'edit' && document.getElementById('registrationSubmit')) {
            submitting = true;
            document.getElementById('registrationSubmit').disabled = true;
        }
    });
    window.addEventListener('pageshow', event => {
        if (event.persisted) { window.location.reload(); return; }
        submitting = false;
        // Account for form values restored by the browser after script initialization.
        if (active && !inherited && (major.value !== state.major || minor.value !== state.minor)) {
            state.major = major.value; state.minor = minor.value;
        }
        render();
    });
    render();
})();
