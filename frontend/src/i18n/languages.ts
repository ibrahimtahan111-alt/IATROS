export const SUPPORTED_LANGUAGES = ['en', 'fr'] as const

export type Language = (typeof SUPPORTED_LANGUAGES)[number]

export const DEFAULT_LANGUAGE: Language = 'en'

/** Language names are shown in their own language, never as flags (internationalization.md §4). */
export const LANGUAGE_OPTIONS: readonly { value: Language; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'fr', label: 'Français' },
]

const STORAGE_KEY = 'iatros.language'

export function isSupportedLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value)
}

function readStoredLanguage(): Language | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isSupportedLanguage(stored) ? stored : null
  } catch {
    return null
  }
}

function detectBrowserLanguage(): Language | null {
  for (const tag of navigator.languages) {
    const base = tag.toLowerCase().split('-')[0]
    if (isSupportedLanguage(base)) {
      return base
    }
  }
  return null
}

/** True once the user has explicitly chosen a language on this device. */
export function hasStoredLanguage(): boolean {
  return readStoredLanguage() !== null
}

export function resolveInitialLanguage(): Language {
  return readStoredLanguage() ?? detectBrowserLanguage() ?? DEFAULT_LANGUAGE
}

export function storeLanguage(language: Language): void {
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // Storage can be unavailable (private mode); the preference then lasts for the session only.
  }
}
