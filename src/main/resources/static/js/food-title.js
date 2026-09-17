(() => {
    'use strict';
    document.querySelectorAll('.tab-heading .detail-title').forEach((heading, index) => {
        const header = heading.closest('.tab-heading');
        const fullName = heading.textContent;
        const trigger = document.createElement('button');
        const text = document.createElement('span');
        const expanded = document.createElement('button');
        trigger.type = expanded.type = 'button';
        trigger.className = 'food-title-trigger';
        text.className = 'food-title-text';
        text.textContent = fullName;
        trigger.append(text);
        expanded.className = 'food-title-expanded';
        expanded.id = `food-title-expanded-${index}`;
        expanded.textContent = fullName;
        expanded.setAttribute('aria-label', `${fullName}, 누르면 닫기`);
        expanded.hidden = true;
        trigger.setAttribute('aria-controls', expanded.id);
        trigger.setAttribute('aria-expanded', 'false');
        heading.replaceChildren(trigger);
        header.nextElementSibling.prepend(expanded);

        function close(restoreFocus = false) {
            expanded.hidden = true;
            trigger.setAttribute('aria-expanded', 'false');
            if (restoreFocus) trigger.focus();
        }
        function fit() {
            text.classList.remove('is-clamped');
            let size = 25;
            heading.style.fontSize = `${size}px`;
            const tooTall = () => text.getBoundingClientRect().height > parseFloat(getComputedStyle(text).lineHeight) * 3 + 1;
            while (size > 16 && tooTall()) heading.style.fontSize = `${--size}px`;
            const clipped = tooTall();
            text.classList.toggle('is-clamped', clipped);
            trigger.disabled = !clipped;
            trigger.setAttribute('aria-label', clipped ? `전체 음식명 확인: ${fullName}` : fullName);
            // The page head hides the title until the size is settled, so it
            // appears once at its final size instead of shrinking on screen.
            heading.style.visibility = 'visible';
            if (!clipped) close();
        }
        trigger.addEventListener('click', () => {
            expanded.hidden = !expanded.hidden;
            trigger.setAttribute('aria-expanded', String(!expanded.hidden));
        });
        expanded.addEventListener('click', () => close(true));
        expanded.addEventListener('keydown', event => {
            if (event.key === 'Escape') close(true);
        });
        trigger.addEventListener('keydown', event => {
            if (event.key === 'Escape') close();
        });
        let lastWidth = -1;
        new ResizeObserver(entries => {
            const width = entries[0].contentRect.width;
            if (width !== lastWidth) { lastWidth = width; fit(); }
        }).observe(heading);
        fit();
        document.fonts.ready.then(fit);
    });
})();
