import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { navItemsFor, navLabelKey } from '../app/navigation'
import { useApplyAccountLanguage } from '../features/auth/useLanguagePreference'
import { useSessionUser } from '../features/auth/session'
import { cx } from '../shared/lib/cx'
import { LogoMark } from '../shared/ui/Logo'
import { ToastProvider } from '../shared/ui/Toast'
import { TooltipProvider } from '../shared/ui/Tooltip'
import styles from './AppShell.module.css'
import { UserMenu } from './UserMenu'

/**
 * IATROS workspace layout (design-system.md §4): navigation rail, main workspace. Below 768px the rail becomes a
 * bottom navigation bar.
 */
export function AppShell() {
  const { t } = useTranslation()
  const user = useSessionUser()
  useApplyAccountLanguage(user)

  const items = navItemsFor(user.role)

  // Tooltips and toasts live here, not in App, to keep them out of the sign-in bundle.
  return (
    <TooltipProvider delayDuration={400}>
      <ToastProvider>
        <div className={styles.shell}>
          <a className={styles.skipLink} href="#main">
            {t('nav.skipToContent')}
          </a>
          <aside className={styles.rail}>
            <div className={styles.brand}>
              <LogoMark />
              <div className={styles.brandText}>
                <span className={styles.product}>{t('app.name')}</span>
                <span className={styles.clinic} title={user.clinic.name}>
                  {user.clinic.name}
                </span>
              </div>
            </div>
            <nav className={styles.nav} aria-label={t('nav.label')}>
              <ul className={styles.navList}>
                {items.map((item) => {
                  const label = t(navLabelKey(item, user.role) as 'nav.overview')
                  return (
                    <li key={item.path}>
                      <NavLink
                        to={item.path}
                        className={({ isActive }) => cx(styles.navLink, isActive && styles.active)}
                        title={label}
                      >
                        <item.icon className={styles.navIcon} aria-hidden="true" />
                        <span className={styles.navLabel}>{label}</span>
                      </NavLink>
                    </li>
                  )
                })}
              </ul>
            </nav>
            <div className={styles.userSlot}>
              <UserMenu user={user} />
            </div>
          </aside>
          <main id="main" className={styles.main} tabIndex={-1}>
            <Outlet />
          </main>
        </div>
      </ToastProvider>
    </TooltipProvider>
  )
}
