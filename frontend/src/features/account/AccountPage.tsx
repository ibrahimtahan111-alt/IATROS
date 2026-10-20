import { useTranslation } from 'react-i18next'
import { LANGUAGE_OPTIONS, type Language } from '../../i18n/languages'
import type { ThemePreference } from '../../shared/theme/theme'
import { useThemePreference } from '../../shared/theme/useThemePreference'
import { ContextBar } from '../../shared/ui/ContextBar'
import { Field } from '../../shared/ui/Field'
import { Select } from '../../shared/ui/Select'
import { useSessionUser } from '../auth/session'
import { useLanguagePreference } from '../auth/useLanguagePreference'
import { DoctorProfileSection } from '../doctors/DoctorProfileSection'
import styles from './AccountPage.module.css'
import { ChangePasswordForm } from './ChangePasswordForm'

/** Profile, preferences, and password for every role (ui-ux.md §38). */
export function AccountPage() {
  const { t } = useTranslation('account')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const [language, setLanguage] = useLanguagePreference(user)
  const [theme, setTheme] = useThemePreference()

  return (
    <div className={styles.page}>
      <ContextBar title={t('title')} />

      <section className={styles.section} aria-labelledby="account-profile">
        <h2 id="account-profile" className={styles.sectionTitle}>
          {t('profile.title')}
        </h2>
        <dl className={styles.details}>
          <div>
            <dt>{t('profile.name')}</dt>
            <dd>
              {user.firstName} {user.lastName}
            </dd>
          </div>
          <div>
            <dt>{t('profile.email')}</dt>
            <dd>{user.email}</dd>
          </div>
          <div>
            <dt>{t('profile.role')}</dt>
            <dd>{tCommon(`roles.${user.role}`)}</dd>
          </div>
          <div>
            <dt>{t('profile.clinic')}</dt>
            <dd>{user.clinic.name}</dd>
          </div>
        </dl>
      </section>

      {user.doctorId && <DoctorProfileSection doctorId={user.doctorId} scheduleLink="/schedules" />}

      <section className={styles.section} aria-labelledby="account-preferences">
        <h2 id="account-preferences" className={styles.sectionTitle}>
          {t('preferences.title')}
        </h2>
        <div className={styles.preferences}>
          <Field label={tCommon('preferences.language')} helper={t('preferences.languageHelp')}>
            {(control) => (
              <Select
                {...control}
                value={language}
                onValueChange={(value) => setLanguage(value as Language)}
                options={LANGUAGE_OPTIONS}
              />
            )}
          </Field>
          <Field label={tCommon('preferences.theme')} helper={t('preferences.themeHelp')}>
            {(control) => (
              <Select
                {...control}
                value={theme}
                onValueChange={(value) => setTheme(value as ThemePreference)}
                options={[
                  { value: 'system', label: tCommon('preferences.themeSystem') },
                  { value: 'light', label: tCommon('preferences.themeLight') },
                  { value: 'dark', label: tCommon('preferences.themeDark') },
                ]}
              />
            )}
          </Field>
        </div>
      </section>

      <section className={styles.section} aria-labelledby="account-password">
        <h2 id="account-password" className={styles.sectionTitle}>
          {t('password.title')}
        </h2>
        <ChangePasswordForm />
      </section>
    </div>
  )
}
