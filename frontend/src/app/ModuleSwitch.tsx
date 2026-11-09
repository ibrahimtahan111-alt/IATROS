import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useSessionUser } from '../features/auth/session'
import { cx } from '../shared/lib/cx'
import styles from './ModuleSwitch.module.css'

/** Overview is always listed first. The current module is marked with the sage accent. */
export function ModuleSwitch({ current }: { current: 'overview' | 'activity' }) {
  const { t } = useTranslation()
  const doctor = useSessionUser().role === 'DOCTOR'
  const items = [
    { id: 'overview' as const, to: '/dashboard', label: t(doctor ? 'nav.myDay' : 'nav.overview') },
    { id: 'activity' as const, to: '/activity', label: t(doctor ? 'nav.myActivity' : 'nav.activity') },
  ]

  return (
    <div className={styles.group} role="group" aria-label={t('moduleSwitch.label')}>
      {items.map((item) => (
        <Link
          key={item.id}
          to={item.to}
          className={cx(styles.item, item.id === current && styles.current)}
          aria-current={item.id === current ? 'page' : undefined}
        >
          {item.label}
        </Link>
      ))}
    </div>
  )
}
