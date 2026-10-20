import { useCallback, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { hasStoredLanguage, isSupportedLanguage, storeLanguage, type Language } from '../../i18n/languages'
import { useUpdatePreferences, type CurrentUser } from './api'

/**
 * Language switching (internationalization.md §4, §33). Before sign-in the choice is stored locally; once signed in,
 * the account preference is authoritative and every change is saved to it.
 */
export function useLanguagePreference(user: CurrentUser | null | undefined): [Language, (language: Language) => void] {
  const { i18n } = useTranslation()
  const updatePreferences = useUpdatePreferences()
  const current = isSupportedLanguage(i18n.resolvedLanguage) ? i18n.resolvedLanguage : 'en'

  const change = useCallback(
    (language: Language) => {
      storeLanguage(language)
      void i18n.changeLanguage(language)
      if (user && user.preferredLanguage !== language) {
        updatePreferences.mutate({ preferredLanguage: language })
      }
    },
    [i18n, user, updatePreferences],
  )

  return [current, change]
}

/** Applies the signed-in user's saved language once per session, falling back to the clinic default. */
export function useApplyAccountLanguage(user: CurrentUser | null | undefined): void {
  const { i18n } = useTranslation()
  const appliedFor = useRef<string | null>(null)

  useEffect(() => {
    if (!user || appliedFor.current === user.id) {
      return
    }
    appliedFor.current = user.id
    const language = user.preferredLanguage ?? (hasStoredLanguage() ? null : user.clinic.defaultLanguage)
    if (language && language !== i18n.resolvedLanguage) {
      if (user.preferredLanguage) {
        storeLanguage(language)
      }
      void i18n.changeLanguage(language)
    }
  }, [user, i18n])
}
