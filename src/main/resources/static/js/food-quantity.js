(() => {
  const form = document.querySelector('[data-quantity-form]');
  if (!form) return;
  const input = document.getElementById('quantityAmount');
  if (input) {
    const maximum = Number(input.dataset.maximum);
    const question = document.getElementById('quantityQuestion');
    const error = document.getElementById('quantityInputError');
    let previous = input.value;
    const verb = input.dataset.action === 'CONSUME' ? '먹은' : '버린';
    const draftValid = value => /^\d{0,9}(?:\.\d{0,3})?$/.test(value)
      && (value === '' || value === '.' || Number(value) <= maximum);
    function validate() {
      const valid = /^\d{1,9}(?:\.\d{1,3})?$/.test(input.value)
        && Number(input.value) > 0 && Number(input.value) <= maximum;
      input.setCustomValidity(valid ? '' : '0보다 크고 기준 수량 이하인 숫자를 입력해줘.');
      question.textContent = input.dataset.action === 'CONSUME' ? '이 구매 항목을 이렇게 기록할까?' : valid
        ? (Number(input.value) === maximum ? `이 구매 항목을 전부 ${verb} 것으로 기록할까?`
          : `이 구매 항목 중 ${input.value}${input.dataset.unit}를 ${verb} 것으로 기록할까?`)
        : `입력한 수량을 ${verb} 것으로 기록할까?`;
      return valid;
    }
    input.addEventListener('beforeinput', event => {
      if (event.data && /[^0-9.]/.test(event.data)) {
        event.preventDefault();
        error.textContent = '숫자와 소수점만 입력할 수 있어.';
      }
    });
    input.addEventListener('input', () => {
      if (!draftValid(input.value)) {
        input.value = previous;
        error.textContent = '음수나 기준 수량을 넘는 값은 입력할 수 없어. 소수점 셋째 자리까지 입력해줘.';
      } else {
        previous = input.value;
        error.textContent = '';
      }
      validate();
    });
    input.addEventListener('blur', () => {
      if (!validate()) error.textContent = input.validationMessage;
    });
    validate();
  }
  let submitting = false;
  form.addEventListener('submit', event => {
    if (submitting) { event.preventDefault(); return; }
    submitting = true;
    form.querySelector('button[type="submit"]').disabled = true;
  });
  window.addEventListener('pageshow', event => {
    if (event.persisted) window.location.reload();
  });
})();
