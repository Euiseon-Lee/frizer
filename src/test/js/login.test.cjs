const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/login.js', 'utf8');

function setup() {
    const csrf = {name: '_csrf', value: 'old-token'};
    const button = {disabled: false, classList: {add() {}, remove() {}}};
    const notice = {hidden: true};
    const events = {}, timers = new Map(), requests = [];
    let submitted = 0, nextTimer = 0, submit;
    const form = {
        dataset: {csrfUrl: '/login/csrf'}, username: 'owner', password: 'test-only-password',
        reportValidity() { return Boolean(this.username && this.password); },
        querySelector: selector => selector.startsWith('input') ? csrf : button,
        addEventListener: (name, fn) => {submit = fn;}, setAttribute() {}, removeAttribute() {}
    };
    vm.runInNewContext(source, {
        document: {querySelector: selector => selector === '[data-login-form]' ? form : notice},
        window: {addEventListener: (name, fn) => {events[name] = fn;}},
        AbortController,
        HTMLFormElement: {prototype: {submit() {submitted++;}}},
        setTimeout: fn => {timers.set(++nextTimer, fn); return nextTimer;},
        clearTimeout: id => timers.delete(id),
        fetch: (url, options) => new Promise((resolve, reject) => requests.push({url, options, resolve, reject}))
    });
    return {csrf, button, notice, form, events, timers, requests,
        submit: () => submit({preventDefault() {}}), count: () => submitted};
}
const tokenResponse = (token = 'fresh-token') => ({ok: true, json: async () => ({parameterName: '_csrf', token})});

test('refreshes token before native submission and ignores double taps', async () => {
    const e = setup();
    const pending = e.submit();
    await e.submit();
    assert.equal(e.requests.length, 1);
    assert.equal(e.count(), 0);
    assert.equal(e.button.disabled, true);
    assert.equal(e.requests[0].options.credentials, 'same-origin');
    assert.equal(e.requests[0].options.cache, 'no-store');
    assert.equal(e.requests[0].options.redirect, 'error');
    assert.equal(e.requests[0].options.body, undefined);
    e.requests[0].resolve(tokenResponse());
    await pending;
    assert.equal(e.csrf.value, 'fresh-token');
    assert.equal(e.count(), 1);
});

test('offline and malformed replies preserve inputs and allow another attempt', async () => {
    const e = setup();
    let pending = e.submit();
    e.requests[0].reject(new Error('offline'));
    await pending;
    assert.equal(e.count(), 0);
    assert.equal(e.button.disabled, false);
    assert.equal(e.notice.hidden, false);
    assert.equal(e.form.username, 'owner');
    assert.equal(e.form.password, 'test-only-password');
    pending = e.submit();
    e.requests[1].resolve({ok: true, json: async () => ({token: 'unusable'})});
    await pending;
    assert.equal(e.csrf.value, 'old-token');
    assert.equal(e.count(), 0);
    assert.equal(e.button.disabled, false);
    pending = e.submit();
    e.requests[2].resolve(tokenResponse());
    await pending;
    assert.equal(e.count(), 1);
});

test('restored pages discard old asynchronous replies and release submit locks', async () => {
    const e = setup();
    const old = e.submit();
    e.events.pagehide();
    e.events.pageshow({persisted: true});
    assert.equal(e.requests[0].options.signal.aborted, true);
    assert.equal(e.button.disabled, false);
    const current = e.submit();
    e.requests[0].resolve(tokenResponse('obsolete'));
    await old;
    assert.equal(e.count(), 0);
    assert.equal(e.button.disabled, true);
    e.requests[1].resolve(tokenResponse('current'));
    await current;
    assert.equal(e.csrf.value, 'current');
    assert.equal(e.count(), 1);
    e.events.pageshow({persisted: true});
    assert.equal(e.button.disabled, false);
});

test('timed-out refresh cannot submit a late token', async () => {
    const e = setup();
    const pending = e.submit();
    [...e.timers.values()][0]();
    e.requests[0].resolve(tokenResponse());
    await pending;
    assert.equal(e.count(), 0);
    assert.equal(e.button.disabled, false);
    assert.equal(e.notice.hidden, false);
});

test('inputs cleared during token refresh are validated again before submission', async () => {
    const e = setup();
    const pending = e.submit();
    e.form.password = '';
    e.requests[0].resolve(tokenResponse());
    await pending;
    assert.equal(e.count(), 0);
    assert.equal(e.button.disabled, false);
    assert.equal(e.notice.hidden, true);
    e.form.password = 'corrected-password';
    const retry = e.submit();
    e.requests[1].resolve(tokenResponse());
    await retry;
    assert.equal(e.count(), 1);
});
