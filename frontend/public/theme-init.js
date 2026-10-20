// Applied before first paint to avoid a flash of the wrong theme or language. Kept as a static file rather than an
// inline script so the production Content-Security-Policy can stay `script-src 'self'` (security.md §27).
;(function () {
  var root = document.documentElement
  var theme = null
  var language = null
  try {
    theme = localStorage.getItem('iatros.theme')
    language = localStorage.getItem('iatros.language')
  } catch (e) {}
  if (theme !== 'light' && theme !== 'dark') {
    theme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  root.dataset.theme = theme
  if (language === 'en' || language === 'fr') {
    root.lang = language
  }
})()
