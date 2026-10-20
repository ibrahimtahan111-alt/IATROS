import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { useLanguagePreference } from '../features/auth/useLanguagePreference'
import { LANGUAGE_OPTIONS } from '../i18n/languages'
import { cx } from '../shared/lib/cx'
import { LogoMark } from '../shared/ui/Logo'
import styles from './AuthLayout.module.css'

/** Sign-in and password pages. The language can be chosen before signing in (internationalization.md §34). */
export function AuthLayout() {
  const { t } = useTranslation()
  const [language, setLanguage] = useLanguagePreference(null)

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles.brand}>
          <LogoMark size={28} />
          <span className={styles.product}>{t('app.name')}</span>
        </div>
        <div className={styles.languages} role="group" aria-label={t('preferences.language')}>
          {LANGUAGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              lang={option.value}
              className={cx(styles.language, option.value === language && styles.languageActive)}
              aria-pressed={option.value === language}
              onClick={() => setLanguage(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </header>
      <main className={styles.main}>
        <div className={styles.panel}>
          <Outlet />
        </div>
      </main>
      <footer className={styles.footer}>{t('app.tagline')}</footer>
    </div>
  )
}
