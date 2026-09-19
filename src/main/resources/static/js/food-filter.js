// '뭐 있지?' food list: filter cards by name locally, like the merge target list.
// The whole list already ships with the page, so no server round-trip is needed.
(() => {
    const filter = document.getElementById('foodFilter');
    const grid = document.getElementById('foodGrid');
    if (!filter || !grid) return;
    const empty = document.getElementById('foodFilterEmpty');
    const count = document.querySelector('.inventory-bar .count');
    const sync = () => {
        const query = filter.value.trim().toLowerCase();
        let visible = 0;
        for (const card of grid.querySelectorAll('.food-card')) {
            const hit = !query || card.dataset.name.toLowerCase().includes(query);
            card.hidden = !hit;
            if (hit) visible++;
        }
        if (empty) empty.hidden = visible > 0;
        if (count) count.textContent = String(visible);
    };
    filter.addEventListener('input', sync);
    // BFCache restores the typed query without an input event; re-apply it.
    window.addEventListener('pageshow', sync);
})();
