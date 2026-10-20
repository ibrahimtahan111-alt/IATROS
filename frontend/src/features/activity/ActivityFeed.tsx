import { CalendarDays, ClipboardClock, FileHeart, Settings, Users, UsersRound, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useSessionUser } from '../auth/session'
import { personName } from '../calendar/api'
import type { ActivityCategory, ActivityItem } from './api'
import styles from './Activity.module.css'

const ICONS: Record<ActivityCategory, LucideIcon> = {
  APPOINTMENTS: CalendarDays,
  PATIENTS: UsersRound,
  CLINICAL: FileHeart,
  SCHEDULES: ClipboardClock,
  TEAM: Users,
  CLINIC: Settings,
}

/** A chronological list of activity entries, newest first. Sentences are built from stable action codes. */
export function ActivityFeed({ items }: { items: readonly ActivityItem[] }) {
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const describe = useDescribeActivity()
  return (
    <ul className={styles.feed}>
      {items.map((item) => {
        const Icon = ICONS[item.category]
        return (
          <li key={item.id} className={styles.entry}>
            <span className={styles.entryIcon} aria-hidden="true">
              <Icon />
            </span>
            <span className={styles.entryBody}>
              <span>{describe(item)}</span>
              <time className={`${styles.entryTime} tabular`} dateTime={item.occurredAt}>
                {format.dateTime(item.occurredAt)}
              </time>
            </span>
          </li>
        )
      })}
    </ul>
  )
}

function useDescribeActivity() {
  const { t } = useTranslation('activity')
  const { t: tCommon } = useTranslation()
  return (item: ActivityItem): string => {
    const values = {
      actor: item.actor ? personName(item.actor) : t('fallback.system'),
      patient: item.patient ? personName(item.patient) : t('fallback.patient'),
      doctor: item.doctor ? personName(item.doctor) : t('fallback.doctor'),
      staff: item.staff ? personName(item.staff) : t('fallback.staff'),
      entry: t(`entry.${item.entityType}`, { defaultValue: t('entry.OTHER') }),
      status: item.status ? tCommon(`appointmentStatus.${item.status}`) : '',
    }
    return t(`event.${item.action}`, {
      ...values,
      defaultValue: t('event.OTHER', values),
    })
  }
}
