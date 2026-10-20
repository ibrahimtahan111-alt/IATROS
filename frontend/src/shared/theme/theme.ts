export type ThemePreference = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'iatros.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

export function readThemePreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}

export function resolveTheme(preference: ThemePreference): ResolvedTheme {
  if (preference !== 'system') {
    return preference
  }
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light'
}

export function applyThemePreference(preference: ThemePreference): void {
  try {
    if (preference === 'system') {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, preference)
    }
  } catch {
    // Storage can be unavailable; the theme still applies for this session.
  }
  document.documentElement.dataset.theme = resolveTheme(preference)
}

/** Keeps the document theme in sync with the OS while the preference is "system". */
export function watchSystemTheme(): () => void {
  const media = window.matchMedia(DARK_QUERY)
  const onChange = () => {
    if (readThemePreference() === 'system') {
      document.documentElement.dataset.theme = resolveTheme('system')
    }
  }
  media.addEventListener('change', onChange)
  return () => media.removeEventListener('change', onChange)
}
