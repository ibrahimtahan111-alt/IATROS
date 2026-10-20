import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { cx } from '../../shared/lib/cx'
import { personName, type CalendarEvent } from './api'
import { datesBetween, isoWeekday, localPoint } from './calendarDates'
import styles from './Calendar.module.css'

interface MonthGridProps {
  from: string
  to: string
  /** "YYYY-MM-DD" of any day in the displayed month. */
  month: string
  events: readonly CalendarEvent[]
  timeZone: string
  today: string
  onDayClick: (date: string) => void
  onEventClick: (id: string) => void
}

const SHOWN_PER_DAY = 3

/** Month overview (ui-ux.md §9): how appointments are distributed; a day opens in the day view. */
export function MonthGrid({ from, to, month, events, timeZone, today, onDayClick, onEventClick }: MonthGridProps) {
  const { t } = useTranslation('calendar')
  const format = useDateTimeFormat(timeZone)
  const dates = datesBetween(from, to)
  const byDate = new Map<string, CalendarEvent[]>()
  for (const event of events) {
    const date = localPoint(event.startAt, timeZone).date
    byDate.set(date, [...(byDate.get(date) ?? []), event])
  }

  return (
    <div className={styles.monthGrid}>
      {dates.slice(0, 7).map((date) => (
        <span key={date} className={styles.weekdayLabel}>
          {format.shortWeekday(isoWeekday(date))}
        </span>
      ))}
      {dates.map((date) => {
        const dayEvents = byDate.get(date) ?? []
        const hidden = dayEvents.length - SHOWN_PER_DAY
        return (
          <div
            key={date}
            className={cx(styles.monthCell, date.slice(0, 7) !== month.slice(0, 7) && styles.outside)}
          >
            <button
              type="button"
              className={cx(styles.dayNumber, date === today && styles.todayNumber)}
              aria-label={format.longDate(date)}
              onClick={() => onDayClick(date)}
            >
              {Number(date.slice(8))}
            </button>
            {dayEvents.slice(0, SHOWN_PER_DAY).map((event) => (
              <button
                key={event.id}
                type="button"
                className={cx(styles.monthEvent, styles[event.status])}
                onClick={() => onEventClick(event.id)}
              >
                <span className="tabular">{format.timeOf(event.startAt)}</span> {personName(event.patient)}
              </button>
            ))}
            {hidden > 0 && (
              <button type="button" className={styles.more} onClick={() => onDayClick(date)}>
                {t('month.more', { count: hidden })}
              </button>
            )}
          </div>
        )
      })}
    </div>
  )
}
