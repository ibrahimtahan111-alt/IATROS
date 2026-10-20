import i18n from 'i18next'
import { SUPPORTED_LANGUAGES } from './languages'
import type { LazyNamespace } from './resources'

type Bundle = { default: Record<string, unknown> }

const bundles = import.meta.glob<Bundle>([
  './locales/*/*.json',
  '!./locales/*/common.json',
  '!./locales/*/auth.json',
  '!./locales/*/errors.json',
])

/**
 * Loads feature namespaces in every supported language, so switching language never waits for the network.
 * Route loaders await this before rendering, so components can use the namespace synchronously.
 */
export async function loadNamespaces(...namespaces: LazyNamespace[]): Promise<void> {
  await Promise.all(
    SUPPORTED_LANGUAGES.flatMap((language) =>
      namespaces.map(async (namespace) => {
        if (i18n.hasResourceBundle(language, namespace)) {
          return
        }
        const load = bundles[`./locales/${language}/${namespace}.json`]
        if (!load) {
          throw new Error(`Missing translations: ${language}/${namespace}`)
        }
        const bundle = await load()
        i18n.addResourceBundle(language, namespace, bundle.default, true, true)
      }),
    ),
  )
}
