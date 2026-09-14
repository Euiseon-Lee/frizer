(() => {
    const root = document.getElementById('mergeContent');
    if (!root) return;
    const select = root.querySelector('#targetId');
    const slot = root.querySelector('#mergePreviewSlot');
    const status = root.querySelector('#mergeStatus');
    const retry = root.querySelector('#mergeRetry');
    let sequence = 0, controller, submitting = false;

    function clear() {
        sequence++;
        controller?.abort();
        slot.replaceChildren();
        retry.hidden = true;
        status.textContent = '';
        slot.setAttribute('aria-busy', 'false');
    }
    async function load() {
        if (submitting) return;
        clear();
        const target = select.value, request = sequence;
        if (!target) return;
        controller = new AbortController();
        status.textContent = '이동할 내용을 확인하고 있어.';
        slot.setAttribute('aria-busy', 'true');
        try {
            const url = new URL(root.dataset.previewUrl, location.origin);
            url.searchParams.set('targetId', target);
            const response = await fetch(url, {signal: controller.signal, cache: 'no-store', credentials: 'same-origin'});
            if (!response.ok || response.redirected) throw new Error('Preview unavailable');
            const html = await response.text();
            if (request !== sequence || target !== select.value) return;
            const parsed = new DOMParser().parseFromString(html, 'text/html');
            const preview = parsed.querySelector('#mergePreview');
            if (!preview || preview.querySelector('[name="targetId"]')?.value !== target ||
                preview.dataset.sourceId !== root.dataset.sourceId) throw new Error('Preview mismatch');
            slot.replaceChildren(document.importNode(preview, true));
            status.textContent = '';
        } catch (error) {
            if (request !== sequence || error.name === 'AbortError') return;
            slot.replaceChildren();
            status.textContent = '이동할 내용을 불러오지 못했어. 다시 시도해줘.';
            retry.hidden = false;
        } finally {
            if (request === sequence) slot.setAttribute('aria-busy', 'false');
        }
    }
    select.addEventListener('change', load);
    retry.addEventListener('click', load);
    root.addEventListener('submit', event => {
        const form = event.target;
        if (!form.matches('#mergeSubmit')) return;
        if (submitting || form.querySelector('[name="targetId"]').value !== select.value) {
            event.preventDefault();
            return;
        }
        submitting = true;
        select.disabled = true;
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        button.textContent = '이동하는 중…';
    });
    // A restored history page must not reuse an old confirmation or request token.
    window.addEventListener('pageshow', event => {
        if (event.persisted) { submitting = false; select.disabled = false; load(); }
    });
})();
