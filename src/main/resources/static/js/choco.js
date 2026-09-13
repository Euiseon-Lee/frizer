// V5 Final A-tier pools from docs/ui-v5/image-pools.json.
const chocoPools = {
  "home": [
    "happy-lounge",
    "happy-sit",
    "proud-sit"
  ],
  "fridge": [
    "leaf-hat-front",
    "leaf-hat-side"
  ],
  "freezer": [
    "cozy-curl",
    "happy-lounge"
  ],
  "room": [
    "proud-sit",
    "look-aside"
  ],
  "allFood": [
    "happy-closeup",
    "puppy-front-paws"
  ],
  "addFood": [
    "come-running",
    "puppy-ready",
    "puppy-front-paws"
  ],
  "success": [
    "happy-sit",
    "proud-sit"
  ],
  "empty": [
    "puppy-tilt",
    "puppy-sit",
    "empty-curious"
  ],
  "rest": [
    "cozy-curl",
    "rest"
  ],
  "loading": [
    "sniff"
  ],
  "history": [
    "look-aside"
  ],
  "profile": [
    "happy-closeup"
  ],
  "registrationBanner": [
    "happy-sit",
    "proud-sit"
  ]
};
const chocoRoleAliases = {hero:'home',all:'allFood',add:'addFood'};
const usedChoco = new Set(['happy-closeup']);
const portraitKeys = new Set(['empty-curious','rest','happy-closeup']);
function applyChocoPlacement(img, key) {
    img.classList.toggle('choco-portrait', portraitKeys.has(key));
    img.classList.toggle('choco-left-edge', key === 'sniff');
    img.classList.remove('choco-wide');
}
document.querySelectorAll('img.choco, .profile-link img').forEach(img => {
    img.addEventListener('error', () => { img.style.visibility = 'hidden'; });
    applyChocoPlacement(img, img.getAttribute('src').split('/').pop().replace('.png',''));
});
document.querySelectorAll('img[data-choco]').forEach(img => {
    const role = chocoRoleAliases[img.dataset.choco] || img.dataset.choco;
    const pool = chocoPools[role] || [];
    const candidates = pool.filter(key => !usedChoco.has(key));
    const key = candidates[Math.floor(Math.random() * candidates.length)] || pool[0];
    if (!key) return;
    usedChoco.add(key);
    const url = new URL(img.getAttribute('src'), location.href);
    url.pathname = url.pathname.replace(/[^/]+\.png$/, key + '.png');
    img.src = url.href;
    if (img.alt) img.alt = '쪼코';
    applyChocoPlacement(img, key);
});