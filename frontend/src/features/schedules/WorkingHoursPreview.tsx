import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { addDays } from '../../shared/lib/localDates'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useWorkingHours } from './api'
import styles from './SchedulesPage.module.css'

const PREVIEW_DAYS = 14

/** The effective hours the appointment system will use: weekly hours with exceptions applied, computed by the server. */
export function WorkingHoursPreview({ doctorId, timeZone, today }: { doctorId: string; timeZone: string; today: string }) {
  const { t } = useTranslation('schedules')
  const { t: tCommon } = useTranslation()
  const format = useDateTimeFormat(timeZone)
  const hours = useWorkingHours(doctorId, today, addDays(today, PREVIEW_DAYS - 1))

  return (
    <Section title={t('preview.title')} description={t('preview.description')}>
      {hours.isPending && (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      )}
      {hours.isError && <ErrorState error={hours.error} onRetry={() => void hours.refetch()} retrying={hours.isFetching} />}
      {hours.data && (
        <ul className={styles.preview} aria-label={t('preview.title')}>
          {hours.data.days.map((day) => (
            <li key={day.date} className={styles.previewDay}>
              <span className={styles.previewDate}>{format.weekdayDate(day.date)}</span>
              <span className={day.ranges.length === 0 ? styles.muted : 'tabular'}>
                {day.ranges.length === 0
                  ? t('preview.off')
                  : day.ranges.map((range) => format.timeRange(range.start, range.end)).join(', ')}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}
