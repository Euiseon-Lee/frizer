(() => {
  document.querySelectorAll('.notice[role="status"]').forEach(notice => {
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'notice-close';
    close.setAttribute('aria-label', '알림 닫기');
    close.textContent = '×';
    notice.append(close);
    notice.classList.add('notice-dismissible');
    notice.addEventListener('click', event => {
      if (event.target.closest('a')) return;
      notice.hidden = true;
    });
  });
})();
