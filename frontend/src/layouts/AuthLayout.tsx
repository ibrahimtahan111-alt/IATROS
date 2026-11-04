import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { useLanguagePreference } from '../features/auth/useLanguagePreference'
import { LANGUAGE_OPTIONS } from '../i18n/languages'
import { cx } from '../shared/lib/cx'
import { LogoLockup, LogoMark } from '../shared/ui/Logo'
import styles from './AuthLayout.module.css'

/** Sign-in and password pages. The language can be chosen before signing in (internationalization.md §34). */
export function AuthLayout() {
  const { t } = useTranslation(['common', 'auth'])
  const [language, setLanguage] = useLanguagePreference(null)

  return (
    <div className={styles.page}>
      <aside className={styles.hero}>
        <div className={styles.scrim} aria-hidden="true" />
        <div className={styles.heroContent}>
          <div className={styles.heroBrand}>
            <LogoLockup size={40} tone="mono" tagline />
          </div>
          <p className={styles.heroLine}>{t('auth:hero')}</p>
        </div>
      </aside>
      <div className={styles.stage}>
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
        <main className={styles.main}>
          <div className={styles.panel}>
            <div className={styles.mark}>
              <LogoMark size={40} />
            </div>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
