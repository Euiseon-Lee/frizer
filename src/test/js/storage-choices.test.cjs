const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function element(value = '') {
    const classes = new Set();
    return {value, checked: false, hidden: false, disabled: false, listeners: {}, attributes: {}, dataset: {},
        addEventListener(type, fn) {(this.listeners[type] ??= []).push(fn)},
        emit(type, event = {}) {for (const fn of this.listeners[type] ?? []) fn(event)},
        setAttribute(name, value) {this.attributes[name] = value},
        removeAttribute(name) {delete this.attributes[name]},
        classList: {toggle(name, enabled) {enabled ? classes.add(name) : classes.delete(name)}, contains(name) {return classes.has(name)}}};
}
function storage({delivery = false, serverError = false, selected = ''} = {}) {
    const group = element(), error = element(), freezer = element(), source = element(delivery ? 'DELIVERY_LEFTOVER' : '');
    const radios = ['ROOM', 'FRIDGE', 'FREEZER'].map(element), date = element('2026-11-01'), window = element();
    error.id = 'storageType-error'; error.hidden = !serverError;
    group.dataset = {errorId: error.id, freezerFields: 'freezerFields', sourceField: 'sourceType'};
    group.attributes['aria-invalid'] = String(serverError);
    for (const radio of radios) {radio.checked = radio.value === selected; radio.attributes['aria-invalid'] = String(serverError); radio.classList.toggle('invalid', serverError)}
    group.querySelectorAll = () => radios; freezer.querySelectorAll = () => [date];
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/storage-choices.js', 'utf8'), {window,
        document: {querySelectorAll: () => [group], getElementById: id => ({'storageType-error': error, freezerFields: freezer, sourceType: source})[id]}});
    return {group, error, freezer, source, radios, date, window,
        choose(value) {radios.forEach(radio => {radio.checked = radio.value === value}); radios.find(radio => radio.checked).emit('change')},
        invalid(index = 0) {group.emit('invalid', {target: radios[index]})}};
}

test('storage: initially unselected; native validation marks the entire group without choosing any radio', () => {
    const e = storage(); assert.equal(e.error.hidden, true); assert.equal(e.freezer.hidden, true);
    e.invalid(); assert.equal(e.group.attributes['aria-invalid'], 'true'); assert.equal(e.error.hidden, false);
    for (const radio of e.radios) {
        assert.equal(radio.checked, false); assert.equal(radio.classList.contains('invalid'), true);
        assert.equal(radio.attributes['aria-invalid'], 'true'); assert.equal(radio.attributes['aria-describedby'], e.error.id);
    }
});
test('storage: server validation stays on all choices until a selection clears the group error', () => {
    for (const value of ['ROOM', 'FRIDGE', 'FREEZER']) {
        const e = storage({serverError: true}); assert.equal(e.error.hidden, false);
        e.choose(value); assert.equal(e.group.attributes['aria-invalid'], 'false'); assert.equal(e.error.hidden, true);
        assert.ok(e.radios.every(radio => !radio.classList.contains('invalid') && radio.attributes['aria-invalid'] === 'false'));
        assert.equal(e.freezer.hidden, value !== 'FREEZER'); assert.equal(e.date.disabled, value !== 'FREEZER');
    }
});
test('storage: switching away from freezer excludes the date while preserving the draft', () => {
    const e = storage(); e.choose('FREEZER'); assert.equal(e.date.disabled, false);
    e.choose('FRIDGE'); assert.equal(e.date.disabled, true); e.choose('FREEZER');
    assert.equal(e.date.value, '2026-11-01'); assert.equal(e.date.disabled, false);
});
test('storage: delivery fallback applies only without an explicit location', () => {
    const e = storage({delivery: true}); assert.equal(e.freezer.hidden, false); assert.ok(e.radios.every(r => !r.checked));
    e.choose('ROOM'); assert.equal(e.freezer.hidden, true); e.choose('FRIDGE'); assert.equal(e.freezer.hidden, true);
    e.choose('FREEZER'); assert.equal(e.freezer.hidden, false);
});
test('storage: restored browser selection resynchronizes error and freezer visibility', () => {
    const e = storage({serverError: true}); e.radios[2].checked = true; e.window.emit('pageshow');
    assert.equal(e.error.hidden, true); assert.equal(e.freezer.hidden, false); assert.equal(e.date.disabled, false);
});
