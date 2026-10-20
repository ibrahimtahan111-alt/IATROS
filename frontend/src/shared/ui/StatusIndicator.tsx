import { useTranslation } from 'react-i18next'
import type { AppointmentStatus } from '../domain/appointmentStatus'
import { cx } from '../lib/cx'
import styles from './StatusIndicator.module.css'

/** Appointment status as a quiet dot plus text label, never color alone (design-system.md §3.2, §11.5). */
export function StatusIndicator({ status, className }: { status: AppointmentStatus; className?: string }) {
  const { t } = useTranslation()
  return (
    <span className={cx(styles.status, styles[status], className)}>
      <span className={styles.dot} aria-hidden="true" />
      {t(`appointmentStatus.${status}`)}
    </span>
  )
}
