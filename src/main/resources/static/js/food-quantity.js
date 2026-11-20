(() => {
  const form = document.querySelector('[data-quantity-form]');
  if (!form) return;
  const input = document.getElementById('quantityAmount');
  if (input) {
    const maximum = Number(input.dataset.maximum);
    const question = document.getElementById('quantityQuestion');
    const error = document.getElementById('quantityInputError');
    const opening = document.getElementById('remainingOpening');
    const openingError = document.getElementById('openingError');
    const opened = document.getElementById('remainingOpened');
    const dateGroup = document.getElementById('openingDateGroup');
    const date = document.getElementById('openedAt');
    const unknownDate = document.getElementById('openingDateUnknown');
    const amountGroup = document.getElementById('openingAmountGroup');
    const openedAmount = document.getElementById('openedQuantity');
    const unopenedAmount = document.getElementById('unopenedQuantity');
    const amountError = document.getElementById('openedQuantityError');
    let manualAmount = openedAmount?.dataset.manual === 'true';
    const numeric = value => /^\d{1,9}(?:\.\d{1,2})?$/.test(value);
    const hundredths = value => Math.round(Number(value) * 100);
    function syncOpeningAmount() {
      if (!amountGroup) return;
      const show = !opening.disabled && opened.checked;
      amountGroup.hidden = amountGroup.disabled = !show;
      if (!show) {
        openedAmount.setCustomValidity('');
        amountError.hidden = true;
        return;
      }
      const remaining = hundredths(maximum) - hundredths(input.value);
      if (!manualAmount) {
        // Counted packages suggest the unfinished unit; weight/volume keep the remainder.
        const measured = /^(g|kg|mg|ml|l|그램|킬로그램|밀리리터|리터)$/i.test(input.dataset.unit);
        openedAmount.value = String((measured ? remaining : remaining % 100 || Math.min(100, remaining)) / 100);
      }
      const amount = hundredths(openedAmount.value);
      const valid = numeric(openedAmount.value) && amount > 0 && amount <= remaining;
      const message = valid ? '' : '개봉 수량은 0보다 크고 남은 수량 이하로 입력해줘.';
      openedAmount.setCustomValidity(message);
      openedAmount.setAttribute('aria-invalid', String(!valid));
      amountError.textContent = message;
      amountError.hidden = valid;
      unopenedAmount.textContent = valid ? `${(remaining - amount) / 100}${input.dataset.unit}` : '—';
    }
    openedAmount?.addEventListener('input', () => {
      manualAmount = true;
      syncOpeningAmount();
    });
    function clearDateError() {
      date.removeAttribute('aria-invalid');
      date.removeAttribute('aria-describedby');
      date.classList.remove('invalid');
      const error = document.getElementById('openedAt-error');
      if (error) error.hidden = true;
    }
    function syncOpeningDate() {
      if (!dateGroup) return;
      const show = !opening.disabled && opened.checked;
      if (!show) {
        date.value = '';
        unknownDate.checked = false;
        clearDateError();
      } else if (dateGroup.hidden) {
        date.value = date.max;
      }
      dateGroup.hidden = dateGroup.disabled = !show;
      date.disabled = !show || unknownDate.checked;
    }
    unknownDate?.addEventListener('change', () => {
      date.value = unknownDate.checked ? '' : date.max;
      syncOpeningDate();
      clearDateError();
    });
    date?.addEventListener('input', clearDateError);
    function clearOpeningError() {
      opening.removeAttribute('aria-invalid');
      opening.removeAttribute('aria-describedby');
      if (openingError) openingError.hidden = true;
    }
    opening?.addEventListener('change', () => {
      clearOpeningError();
      syncOpeningDate();
      syncOpeningAmount();
    });
    opening?.addEventListener('invalid', () => {
      opening.setAttribute('aria-invalid', 'true');
      opening.setAttribute('aria-describedby', 'openingError');
      openingError.hidden = false;
    }, true);
    let previous = input.value;
    const verb = input.dataset.action === 'CONSUME' ? '먹은' : '버린';
    const draftValid = value => /^\d{0,9}(?:\.\d{0,2})?$/.test(value)
      && (value === '' || value === '.' || Number(value) <= maximum);
    function validate() {
      const valid = /^\d{1,9}(?:\.\d{1,2})?$/.test(input.value)
        && Number(input.value) > 0 && Number(input.value) <= maximum;
      input.setCustomValidity(valid ? '' : '0보다 크고 기준 수량 이하인 숫자를 입력해줘.');
      if (opening) {
        const showOpening = valid && Number(input.value) < maximum;
        opening.hidden = opening.disabled = !showOpening;
        if (!showOpening) {
          for (const radio of opening.querySelectorAll('input[type="radio"]')) radio.checked = radio.value === 'UNOPENED';
          clearOpeningError();
          manualAmount = false;
        }
        syncOpeningDate();
        syncOpeningAmount();
      }
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
        return;
      }
      if (event.data && input.selectionStart != null) {
        const next = input.value.slice(0, input.selectionStart) + event.data
          + input.value.slice(input.selectionEnd);
        if (/^\d*\.\d{3,}$/.test(next)) {
          event.preventDefault();
          error.textContent = '';
        }
      }
    });
    input.addEventListener('input', () => {
      const caret = input.selectionStart;
      const decimal = input.value.match(/^(\d*)\.(\d{3,})$/);
      if (decimal) {
        input.value = decimal[1] + '.' + decimal[2].slice(0, 2);
        const position = Math.min(caret ?? input.value.length, input.value.length);
        input.setSelectionRange(position, position);
      }
      if (!draftValid(input.value)) {
        input.value = previous;
        error.textContent = '음수나 기준 수량을 넘는 값은 입력할 수 없어.';
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
    const categoryLink = document.getElementById('quantityCategoryLink');
    if (categoryLink) {
      const syncCategoryLink = () => {
        const url = new URL(categoryLink.href);
        url.searchParams.set('returnQuantity', input.value);
        categoryLink.href = url.toString();
      };
      input.addEventListener('input', syncCategoryLink);
      categoryLink.addEventListener('click', event => {
        if (!validate()) {
          event.preventDefault();
          error.textContent = input.validationMessage;
          input.focus();
          return;
        }
        syncCategoryLink();
      });
      syncCategoryLink();
    }
  }
  let submitting = false;
  form.addEventListener('submit', event => {
    if (submitting || form.querySelector('button[type="submit"]').disabled) { event.preventDefault(); return; }
    submitting = true;
    form.querySelector('button[type="submit"]').disabled = true;
  });
  window.addEventListener('pageshow', event => {
    if (event.persisted) window.location.reload();
  });
})();
