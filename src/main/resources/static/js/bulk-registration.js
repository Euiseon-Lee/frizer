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
    function notify(text, error = false, busy = false) {
        message.textContent = text;
        message.hidden = !text;
        message.className = (error ? 'field-error bulk-upload-error' : 'help bulk-help') + (busy ? ' busy-indicator' : '');
    }
    function invalidate() {
        previewReady = false;
        syncCommit();
    }
    results.addEventListener('change', event => {
        if (event.target === document.getElementById('bulkRepeat')) syncCommit();
    });
    // The commit button's in-flight lock and spinner come from the shared busy-buttons.js.
    results.addEventListener('submit', event => {
        syncCommit();
        if (document.getElementById('bulkComplete')?.disabled) event.preventDefault();
    });
    // A BFCache return would revive a stale, session-bound preview; reload instead.
    window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload(); });
    upload.addEventListener('submit', event => event.preventDefault());
    // The template is generated per request and the free-tier server can take a while;
    // fetch it ourselves so the button stays locked until the file actually arrives.
    const template = document.querySelector('.bulk-template-download');
    if (template) {
        const label = template.textContent;
        let downloading = false;
        template.addEventListener('click', async event => {
            event.preventDefault();
            if (downloading) return;
            downloading = true;
            template.setAttribute('aria-disabled', 'true');
            template.classList.add('busy-indicator');
            template.textContent = '양식을 준비하고 있어…';
            try {
                const response = await fetch(template.href, {credentials: 'same-origin'});
                if (response.redirected && new URL(response.url).pathname.endsWith('/login')) {
                    window.location.assign(response.url);
                    return;
                }
                if (!response.ok) throw new Error('template');
                const url = URL.createObjectURL(await response.blob());
                const link = document.createElement('a');
                link.href = url;
                link.download = 'frizer-bulk-template.xlsx';
                document.body.append(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
            } catch (error) {
                notify('양식을 내려받지 못했어. 잠시 후 다시 시도해줘.', true);
            } finally {
                downloading = false;
                template.removeAttribute('aria-disabled');
                template.classList.remove('busy-indicator');
                template.textContent = label;
            }
        });
    }
    input.addEventListener('change', async () => {
        pending?.abort();
        const request = ++revision;
        upload.removeAttribute('aria-busy');
        invalidate();
        const previous = document.getElementById('bulkPreview');
        if (previous) previous.hidden = true;
        const help = document.getElementById('bulkCompletionHelp');
        if (help) help.hidden = true;
        notify('');
        if (!input.files.length || !upload.reportValidity()) return;
        pending = new AbortController();
        upload.setAttribute('aria-busy', 'true');
        notify('미리보기를 준비하고 있어.', false, true);
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
