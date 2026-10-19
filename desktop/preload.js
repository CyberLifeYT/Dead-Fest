// Preload: intentionally minimal. The renderer talks to Firebase over HTTPS directly,
// so it needs no Node APIs exposed to it.
window.electronVersion = process.versions.electron;
