import i18n, { type BackendModule } from 'i18next'
import { initReactI18next } from 'react-i18next'
import { DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES, resolveInitialLanguage } from './languages'
import { defaultNamespace, namespaces, resources } from './resources'

type Bundle = { default: Record<string, unknown> }

const unbundled = import.meta.glob<Bundle>(['./locales/*/*.json', '!./locales/en/common.json', '!./locales/en/auth.json', '!./locales/en/errors.json'])

/**
 * Fetches translations that are not in the sign-in bundle (e.g. French core namespaces) when i18next first needs
 * them, so changeLanguage resolves only once the language is ready.
 */
const lazyTranslations: BackendModule = {
  type: 'backend',
  init: () => undefined,
  read: (language, namespace, callback) => {
    const load = unbundled[`./locales/${language}/${namespace}.json`]
    if (!load) {
      callback(new Error(`Missing translations: ${language}/${namespace}`), false)
      return
    }
    load().then(
      (bundle) => callback(null, bundle.default),
      (error: unknown) => callback(error instanceof Error ? error : new Error(String(error)), false),
    )
  },
}

i18n.on('languageChanged', (language) => {
  document.documentElement.lang = language
})

/** Resolves once the initial language is loaded; render after it so the first paint is already translated. */
export const i18nReady = i18n
  .use(lazyTranslations)
  .use(initReactI18next)
  .init({
    resources,
    partialBundledLanguages: true,
    lng: resolveInitialLanguage(),
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: SUPPORTED_LANGUAGES,
    ns: namespaces,
    defaultNS: defaultNamespace,
    interpolation: { escapeValue: false },
  })

export default i18n
