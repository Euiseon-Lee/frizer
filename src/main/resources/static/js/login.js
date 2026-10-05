// A restored or long-lived login form may belong to an older session.
(() => {
    const form = document.querySelector('[data-login-form]');
    const csrf = form?.querySelector('input[name="_csrf"]');
    if (!csrf) return; // Local profiles may explicitly disable authentication/CSRF.
    const button = form.querySelector('button[type="submit"]');
    const notice = document.querySelector('[data-login-retry]');
    let active = null;

    const unlock = () => {
        button.disabled = false;
        button.classList.remove('busy-indicator');
        form.removeAttribute('aria-busy');
    };
    const reset = () => {
        const previous = active;
        active = null;
        previous?.abort();
        unlock();
    };
    window.addEventListener('pagehide', reset);
    window.addEventListener('pageshow', reset);

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (active) return;
        const controller = new AbortController();
        active = controller;
        button.disabled = true;
        button.classList.add('busy-indicator');
        form.setAttribute('aria-busy', 'true');
        notice.hidden = true;
        const timeout = setTimeout(() => controller.abort(), 60000);
        try {
            const response = await fetch(form.dataset.csrfUrl, {
                credentials: 'same-origin', cache: 'no-store', redirect: 'error',
                headers: {Accept: 'application/json'}, signal: controller.signal
            });
            if (!response.ok) throw new Error('Token refresh failed');
            const token = await response.json();
            if (active !== controller) return;
            if (controller.signal.aborted) throw new Error('Token refresh timed out');
            if (token.parameterName !== csrf.name || typeof token.token !== 'string' || !token.token)
                throw new Error('Invalid token response');
            csrf.value = token.token;
            // Inputs may have changed while the token request was pending.
            if (!form.reportValidity()) {
                active = null;
                unlock();
                return;
            }
            HTMLFormElement.prototype.submit.call(form);
        } catch (_) {
            if (active === controller) {
                active = null;
                unlock();
                notice.hidden = false; // Keep entered credentials only in the existing form.
            }
        } finally {
            clearTimeout(timeout);
        }
    });
})();
