import type { CSSProperties, MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { cx } from '../../shared/lib/cx'
import { minutesOf } from '../../shared/lib/localDates'
import { personName, type CalendarEvent, type TimeRange } from './api'
import { clockOf, layoutLanes, MINUTES_PER_DAY, spanOn } from './calendarDates'
import styles from './Calendar.module.css'

export interface GridColumn {
  key: string
  date: string
  label: string
  /** The doctor this column shows; null when it shows every displayed doctor (week view). */
  doctorId: string | null
  /** Working hours of the column, used to shade the rest of the day. */
  working: readonly TimeRange[]
}

interface TimeGridProps {
  columns: readonly GridColumn[]
  events: readonly CalendarEvent[]
  timeZone: string
  /** Clinic-local "now", for the Now line. */
  now: { date: string; minutes: number }
  /** Doctor names to show on events when a column mixes several doctors. */
  doctorNames: ReadonlyMap<string, string> | null
  onSlotClick: (column: GridColumn, minutes: number) => void
  onEventClick: (id: string) => void
}

const SLOT_MINUTES = 15
const DEFAULT_START = 7 * 60
const DEFAULT_END = 20 * 60

/**
 * Day and week time grid (design-system.md §11.7): hour lines, non-working time on a sunken background, the Now
 * line, and overlapping appointments stacked vertically at full width. Clicking free space books at that time.
 */
export function TimeGrid({ columns, events, timeZone, now, doctorNames, onSlotClick, onEventClick }: TimeGridProps) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const format = useDateTimeFormat(timeZone)

  const placed = columns.map((column) =>
    layoutLanes(
      events
        .filter((event) => column.doctorId === null || event.doctorId === column.doctorId)
        .flatMap((event) => {
          const span = spanOn(event, column.date, timeZone)
          return span ? [{ item: event, ...span }] : []
        }),
    ),
  )
  const [first, last] = visibleMinutes(columns, placed.flat())
  const hours = Array.from({ length: (last - first) / 60 }, (_, index) => first + index * 60)

  const slotFrom = (column: GridColumn) => (event: MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const ratio = rect.height > 0 ? (event.clientY - rect.top) / rect.height : 0
    const minutes = first + Math.floor((ratio * (last - first)) / SLOT_MINUTES) * SLOT_MINUTES
    onSlotClick(column, Math.min(Math.max(minutes, first), last - SLOT_MINUTES))
  }

  return (
    <div className={styles.timeGrid} style={{ '--columns': columns.length } as CSSProperties}>
      <div className={styles.gridHeader}>
        <span />
        {columns.map((column) => (
          <span key={column.key} className={cx(styles.columnLabel, column.date === now.date && styles.today)}>
            {column.label}
          </span>
        ))}
      </div>
      <div className={styles.gridBody} style={{ '--span': last - first } as CSSProperties}>
        <div className={styles.timeAxis} aria-hidden="true">
          {hours.map((minute) => (
            <span key={minute} className={styles.hourLabel} style={position(minute - first)}>
              {format.time(clockOf(minute))}
            </span>
          ))}
        </div>
        {columns.map((column, index) => (
          <div
            key={column.key}
            className={styles.column}
            onClick={slotFrom(column)}
            role="presentation"
            data-testid={`column-${column.key}`}
          >
            {column.working.map((range) => {
              const start = Math.max(minutesOf(range.start), first)
              const end = Math.min(minutesOf(range.end), last)
              return end > start ? (
                <div key={range.start} className={styles.working} style={position(start - first, end - start)} />
              ) : null
            })}
            {hours.map((minute) => (
              <div key={minute} className={styles.hourLine} style={position(minute - first)} />
            ))}
            {column.date === now.date && now.minutes >= first && now.minutes <= last && (
              <div className={styles.nowLine} style={position(now.minutes - first)} aria-hidden="true" />
            )}
            {placed[index]!.map(({ item: event, start, end, lane, lanes }) => (
              <button
                key={event.id}
                type="button"
                className={cx(styles.event, styles[event.status])}
                style={
                  {
                    ...position(start - first, Math.max(end - start, SLOT_MINUTES)),
                    '--lane': lane,
                    '--lanes': lanes,
                  } as CSSProperties
                }
                onClick={(click) => {
                  click.stopPropagation()
                  onEventClick(event.id)
                }}
              >
                <span className={styles.eventPatient}>{personName(event.patient)}</span>
                <span className={styles.eventMeta}>
                  <span className="tabular">{format.timeRangeOf(event.startAt, event.endAt)}</span>
                  {' · '}
                  {t(`type.${event.type}`)}
                  {doctorNames && doctorNames.size > 1 && <> · {doctorNames.get(event.doctorId)}</>}
                </span>
                <span className="visually-hidden">{tCommon(`appointmentStatus.${event.status}`)}</span>
              </button>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

/** The hours to draw: at least the default day, widened to every working range and appointment shown. */
function visibleMinutes(columns: readonly GridColumn[], spans: readonly { start: number; end: number }[]): [number, number] {
  let first = DEFAULT_START
  let last = DEFAULT_END
  for (const column of columns) {
    for (const range of column.working) {
      first = Math.min(first, minutesOf(range.start))
      last = Math.max(last, minutesOf(range.end))
    }
  }
  for (const span of spans) {
    first = Math.min(first, span.start)
    last = Math.max(last, span.end)
  }
  return [Math.floor(first / 60) * 60, Math.min(Math.ceil(last / 60) * 60, MINUTES_PER_DAY)]
}

function position(top: number, height?: number): CSSProperties {
  return { '--top': top, ...(height === undefined ? {} : { '--height': height }) } as CSSProperties
}