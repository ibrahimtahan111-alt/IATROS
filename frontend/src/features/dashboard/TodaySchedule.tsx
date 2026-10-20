import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Badge } from '../../shared/ui/Badge'
import { StatusIndicator } from '../../shared/ui/StatusIndicator'
import { personName } from '../calendar/api'
import type { ScheduleItem } from './api'
import styles from './Dashboard.module.css'

interface TodayScheduleProps {
  items: readonly ScheduleItem[]
  today: string
  timeZone: string
  /** The owner's list spans doctors; a doctor's is their own. */
  showDoctor: boolean
}

/** Today's appointments in time order; each opens in the calendar's day view. */
export function TodaySchedule({ items, today, timeZone, showDoctor }: TodayScheduleProps) {
  const { t } = useTranslation('calendar')
  const { t: tDashboard } = useTranslation('dashboard')
  const format = useDateTimeFormat(timeZone)
  return (
    <ul className={styles.schedule}>
      {items.map((item) => (
        <li key={item.id} className={styles.scheduleItem}>
          <span className={`${styles.scheduleTime} tabular`}>{format.timeOf(item.startAt)}</span>
          <span className={styles.scheduleBody}>
            <Link className={styles.scheduleLink} to={`/calendar?view=day&date=${today}&appointment=${item.id}`}>
              {personName(item.patient)}
            </Link>
            <span className={styles.muted}>
              {showDoctor ? `${personName(item.doctor)} · ` : ''}
              {t(`type.${item.type}`)}
            </span>
            {item.alerts && (item.alerts.severeAllergies.length > 0 || item.alerts.activeConditions.length > 0) && (
              <span className={styles.alerts}>
                {item.alerts.severeAllergies.map((allergen) => (
                  <Badge key={`allergy-${allergen}`} tone="danger">
                    {tDashboard('alerts.allergy', { name: allergen })}
                  </Badge>
                ))}
                {item.alerts.activeConditions.map((condition) => (
                  <Badge key={`condition-${condition}`} tone="info">
                    {condition}
                  </Badge>
                ))}
              </span>
            )}
          </span>
          <StatusIndicator status={item.status} />
        </li>
      ))}
    </ul>
  )
}
