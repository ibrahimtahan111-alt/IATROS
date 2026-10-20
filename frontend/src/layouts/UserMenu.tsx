import { ChevronsUpDown, LogOut, UserRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useLogout, type CurrentUser } from '../features/auth/api'
import { useLanguagePreference } from '../features/auth/useLanguagePreference'
import { LANGUAGE_OPTIONS } from '../i18n/languages'
import { useThemePreference } from '../shared/theme/useThemePreference'
import type { ThemePreference } from '../shared/theme/theme'
import { Menu, MenuItem, MenuRadioGroup, MenuSeparator } from '../shared/ui/Menu'
import styles from './UserMenu.module.css'

/** Account, language, theme, and sign-out (design-system.md §4.1–4.2). */
export function UserMenu({ user }: { user: CurrentUser }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const logout = useLogout()
  const [language, setLanguage] = useLanguagePreference(user)
  const [theme, setTheme] = useThemePreference()
  const fullName = `${user.firstName} ${user.lastName}`
  const initials = `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase()

  return (
    <Menu
      side="top"
      align="start"
      trigger={
        <button type="button" className={styles.trigger} aria-label={t('userMenu.open', { name: fullName })}>
          <span className={styles.avatar} aria-hidden="true">
            {initials}
          </span>
          <span className={styles.identity}>
            <span className={styles.name}>{fullName}</span>
            <span className={styles.role}>{t(`roles.${user.role}`)}</span>
          </span>
          <ChevronsUpDown className={styles.chevron} aria-hidden="true" />
        </button>
      }
    >
      <MenuItem icon={<UserRound />} onSelect={() => navigate('/account')}>
        {t('userMenu.account')}
      </MenuItem>
      <MenuSeparator />
      <MenuRadioGroup
        label={t('preferences.language')}
        value={language}
        onValueChange={(value) => setLanguage(value as typeof language)}
        options={LANGUAGE_OPTIONS}
      />
      <MenuSeparator />
      <MenuRadioGroup
        label={t('preferences.theme')}
        value={theme}
        onValueChange={(value) => setTheme(value as ThemePreference)}
        options={[
          { value: 'system', label: t('preferences.themeSystem') },
          { value: 'light', label: t('preferences.themeLight') },
          { value: 'dark', label: t('preferences.themeDark') },
        ]}
      />
      <MenuSeparator />
      <MenuItem icon={<LogOut />} onSelect={() => logout.mutate()}>
        {t('userMenu.signOut')}
      </MenuItem>
    </Menu>
  )
}
