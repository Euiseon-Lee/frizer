(() => {
    const input = document.getElementById('bulkFile');
    const upload = document.querySelector('[data-bulk-upload]');
    if (!input || !upload) return;
    const message = document.getElementById('bulkUploadMessage');
    const results = document.getElementById('bulkResults');
    let revision = 0;
    let pending;
    let previewReady = !!document.getElementById('bulkCommitForm');
    function syncCommit() {
        const complete = document.getElementById('bulkComplete');
        const repeat = document.getElementById('bulkRepeat');
        if (complete) complete.disabled = !previewReady || !!(repeat && !repeat.checked);
    }
    syncCommit();
    function notify(text, error = false) {
        message.textContent = text;
        message.hidden = !text;
        message.className = error ? 'field-error bulk-upload-error' : 'help bulk-help';
    }
    function invalidate() {
        previewReady = false;
        syncCommit();
    }
    results.addEventListener('change', event => {
        if (event.target === document.getElementById('bulkRepeat')) syncCommit();
    });
    results.addEventListener('submit', event => {
        syncCommit();
        if (document.getElementById('bulkComplete')?.disabled) event.preventDefault();
    });
    upload.addEventListener('submit', event => event.preventDefault());
    input.addEventListener('change', async () => {
        revision++;
        pending?.abort();
        upload.removeAttribute('aria-busy');
        const error = input.files.length > 1 ? '엑셀 파일은 1개만 선택해줘.' : '';
        input.setCustomValidity(error);
        invalidate();
        const previous = document.getElementById('bulkPreview');
        if (previous) previous.hidden = true;
        const help = document.getElementById('bulkCompletionHelp');
        if (help) help.hidden = true;
        notify(error, !!error);
        if (input.files.length !== 1 || !upload.reportValidity()) return;
        pending?.abort();
        const request = ++revision;
        pending = new AbortController();
        invalidate();
        upload.setAttribute('aria-busy', 'true');
        notify('미리보기를 준비하고 있어.');
        try {
            const csrf = upload.querySelector('input[name="_csrf"]');
            const response = await fetch(upload.action, {
                method: 'POST', body: new FormData(upload), credentials: 'same-origin', signal: pending.signal,
                headers: csrf ? {'X-CSRF-TOKEN': csrf.value} : {}
            });
            if (response.redirected && new URL(response.url).pathname.endsWith('/login')) {
                window.location.assign(response.url);
                return;
            }
            if (!response.ok) throw new Error('preview');
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            if (request !== revision) return;
            const next = page.getElementById('bulkResults');
            const token = page.querySelector('[data-bulk-upload] input[name="formToken"]');
            if (!next || !token) throw new Error('preview');
            upload.querySelector('input[name="formToken"]').value = token.value;
            results.replaceChildren(...Array.from(next.childNodes));
            previewReady = !!document.getElementById('bulkCommitForm');
            syncCommit();
            const error = page.getElementById('bulkUploadMessage')?.textContent.trim() || '';
            notify(error, !!error);
            if (error) input.value = '';
            const preview = document.getElementById('bulkPreview');
            if (preview) {
                preview.scrollIntoView({behavior: 'smooth', block: 'start'});
                preview.focus({preventScroll: true});
            }
        } catch (error) {
            if (request === revision && error.name !== 'AbortError') {
                notify('미리보기를 불러오지 못했어. 파일을 다시 선택해줘.', true);
                input.value = '';
            }
        } finally {
            if (request === revision) {
                upload.removeAttribute('aria-busy');
            }
        }
    });
})();
