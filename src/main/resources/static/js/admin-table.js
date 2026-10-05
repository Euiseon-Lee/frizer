// Fit the header and the first ten rows, including wrapped cells and horizontal scrollbars.
(() => {
    document.querySelectorAll('.admin-log-scroll[data-visible-rows]').forEach(container => {
        const table = container.querySelector('table');
        const visibleRows = Number(container.dataset.visibleRows);
        if (!table || !Number.isInteger(visibleRows) || visibleRows < 1) return;
        const resize = () => {
            const rows = table.tBodies[0]?.rows;
            if (!rows || rows.length <= visibleRows) {
                container.style.maxHeight = '';
                return;
            }
            const rowBottom = rows[visibleRows - 1].getBoundingClientRect().bottom;
            const tableTop = table.getBoundingClientRect().top;
            const horizontalScrollbar = container.offsetHeight - container.clientHeight;
            container.style.maxHeight = `${Math.ceil(rowBottom - tableTop + horizontalScrollbar)}px`;
        };
        resize();
        new ResizeObserver(resize).observe(table);
    });
})();
