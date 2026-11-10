import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { cx } from '../shared/lib/cx'
import styles from './ModuleSwitch.module.css'

/** Team, schedules, and settings are one Configuration module. The current section keeps the sage accent. */
export function ConfigurationSwitch({ current }: { current: 'team' | 'schedules' | 'settings' }) {
  const { t } = useTranslation()
  const items = [
    { id: 'team' as const, to: '/team', label: t('nav.team') },
    { id: 'schedules' as const, to: '/schedules', label: t('nav.schedules') },
    { id: 'settings' as const, to: '/settings', label: t('nav.settings') },
  ]

  return (
    <div className={styles.group} role="group" aria-label={t('moduleSwitch.configuration')}>
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
