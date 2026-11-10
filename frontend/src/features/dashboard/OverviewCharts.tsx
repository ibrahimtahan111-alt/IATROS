import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { cx } from '../../shared/lib/cx'
import { personName } from '../calendar/api'
import type { DaySummary, OwnerDashboard, ScheduleItem } from './api'
import styles from './Dashboard.module.css'

const STATUS_SLICES = [
  { key: 'waiting', tone: 'waiting', status: 'WAITING', color: 'var(--color-status-waiting)' },
  { key: 'consult', tone: 'consult', status: 'IN_CONSULTATION', color: 'var(--color-status-in-consultation)' },
  { key: 'next', tone: 'next', status: 'CONFIRMED', color: 'var(--color-status-scheduled)' },
  { key: 'done', tone: 'done', status: 'COMPLETED', color: 'var(--color-status-completed)' },
  { key: 'missed', tone: 'missed', status: 'NO_SHOW', color: 'var(--color-status-no-show)' },
  { key: 'cancelled', tone: 'cancelled', status: 'CANCELLED', color: 'var(--color-status-cancelled)' },
] as const

type SliceKey = (typeof STATUS_SLICES)[number]['key']

function countOf(summary: DaySummary, key: SliceKey): number {
  switch (key) {
    case 'waiting':
      return summary.waiting
    case 'consult':
      return summary.inConsultation
    case 'next':
      return summary.remaining
    case 'done':
      return summary.completed
    case 'missed':
      return summary.noShows
    case 'cancelled':
      return summary.cancelled
  }
}

function hourInZone(iso: string, timeZone: string): number {
  const hour = new Intl.DateTimeFormat('en-US', { hour: 'numeric', hourCycle: 'h23', timeZone })
    .formatToParts(new Date(iso))
    .find((part) => part.type === 'hour')
  return Number(hour?.value ?? 0)
}

/** Drawn bottom-to-top in a column and left-to-right in a bar, so a no-show sits on the end. */
const STACK = [
  { key: 'done', tone: 'done', color: 'var(--color-status-completed)', statuses: ['COMPLETED'] },
  { key: 'consult', tone: 'consult', color: 'var(--color-status-in-consultation)', statuses: ['IN_CONSULTATION'] },
  { key: 'waiting', tone: 'waiting', color: 'var(--color-status-waiting)', statuses: ['WAITING'] },
  { key: 'next', tone: 'next', color: 'var(--color-status-scheduled)', statuses: ['SCHEDULED', 'CONFIRMED'] },
  { key: 'missed', tone: 'missed', color: 'var(--color-status-no-show)', statuses: ['NO_SHOW'] },
] as const

type StackKey = (typeof STACK)[number]['key']

interface ChartPart {
  key: string
  tone: string
  color: string
  count: number
}

function emptyStack(): Record<StackKey, number> {
  return { done: 0, consult: 0, waiting: 0, next: 0, missed: 0 }
}

function stackOf(status: ScheduleItem['status']): StackKey | null {
  const found = STACK.find((part) => (part.statuses as readonly string[]).includes(status))
  return found?.key ?? null
}

function partsFrom(counts: Record<StackKey, number>): ChartPart[] {
  return STACK.filter((part) => counts[part.key] > 0).map((part) => ({ ...part, count: counts[part.key] }))
}

/** Clinic day window, widened when an appointment falls outside it. */
function hourBuckets(
  items: readonly ScheduleItem[],
  timeZone: string,
): { hour: number; count: number; missed: number; parts: ChartPart[] }[] {
  const byHour = new Map<number, Record<StackKey, number>>()
  for (const item of items) {
    const key = stackOf(item.status)
    if (!key) continue
    const hour = hourInZone(item.startAt, timeZone)
    const counts = byHour.get(hour) ?? emptyStack()
    counts[key] += 1
    byHour.set(hour, counts)
  }
  if (byHour.size === 0) return []
  const present = [...byHour.keys()]
  const from = Math.min(8, ...present)
  const to = Math.max(18, ...present)
  return Array.from({ length: to - from + 1 }, (_, index) => {
    const hour = from + index
    const counts = byHour.get(hour) ?? emptyStack()
    const parts = partsFrom(counts)
    return {
      hour,
      count: parts.reduce((sum, part) => sum + part.count, 0),
      missed: counts.missed,
      parts,
    }
  })
}

function hourLabel(hour: number, language: string): string {
  return new Intl.DateTimeFormat(language, { hour: 'numeric', hourCycle: 'h23', timeZone: 'UTC' }).format(
    new Date(Date.UTC(2026, 0, 1, hour)),
  )
}

/** Statuses actually drawn. A single completed run needs no legend; a no-show always does. */
function legendKeys(groups: readonly { parts: readonly ChartPart[] }[]): string[] {
  const present = new Set(groups.flatMap((group) => group.parts.map((part) => part.key)))
  const ordered = ['done', 'consult', 'progress', 'waiting', 'next', 'missed'].filter((key) => present.has(key))
  if (ordered.length < 2 && !present.has('missed')) return []
  return ordered
}

function doctorParts(row: OwnerDashboard['doctors'][number]): ChartPart[] {
  return [
    { key: 'done', tone: 'done', color: 'var(--color-status-completed)', count: row.completed },
    { key: 'progress', tone: 'consult', color: 'var(--color-status-in-consultation)', count: row.inProgress },
    { key: 'next', tone: 'next', color: 'var(--color-status-scheduled)', count: row.remaining },
    { key: 'missed', tone: 'missed', color: 'var(--color-status-no-show)', count: row.noShows },
  ].filter((part) => part.count > 0)
}

/** Flex weights. A no-show keeps at least a sliver so one missed visit stays readable. */
function paint(parts: readonly ChartPart[]): (ChartPart & { weight: number })[] {
  const total = parts.reduce((sum, part) => sum + part.count, 0)
  if (total === 0) return []
  const raw = parts.map((part) => ({
    part,
    weight: Math.max(part.count / total, part.key === 'missed' ? 0.14 : 0),
  }))
  const scale = raw.reduce((sum, entry) => sum + entry.weight, 0)
  return raw.map(({ part, weight }) => ({ ...part, weight: weight / scale }))
}

/** Diagrams for the overview: today's mix, when the day fills up, and each doctor's load. */
export function OverviewCharts({
  summary,
  schedule,
  timeZone,
  doctors,
  hoursPartial,
}: {
  summary: DaySummary
  schedule: readonly ScheduleItem[]
  timeZone: string
  doctors?: OwnerDashboard['doctors']
  /** The listed schedule is not the whole day, so the histogram is a sample. */
  hoursPartial?: boolean
}) {
  const { t, i18n } = useTranslation('dashboard')
  const { t: tCommon } = useTranslation()
  const number = useNumberFormat()
  const sliceLabel = (key: SliceKey, status: (typeof STATUS_SLICES)[number]['status']) =>
    key === 'next' ? t('today.remaining') : tCommon(`appointmentStatus.${status}`)
  const partLabel = (key: string) => {
    switch (key) {
      case 'done':
        return tCommon('appointmentStatus.COMPLETED')
      case 'consult':
        return tCommon('appointmentStatus.IN_CONSULTATION')
      case 'waiting':
        return tCommon('appointmentStatus.WAITING')
      case 'next':
        return t('today.remaining')
      case 'progress':
        return t('charts.inProgress')
      case 'missed':
        return tCommon('appointmentStatus.NO_SHOW')
      default:
        return key
    }
  }
  const toneOf = (key: string): 'waiting' | 'consult' | 'next' | 'done' | 'missed' =>
    key === 'progress' ? 'consult' : (STACK.find((part) => part.key === key)?.tone ?? 'done')
  const slices = STATUS_SLICES.map((slice) => ({
    ...slice,
    count: countOf(summary, slice.key),
    label: sliceLabel(slice.key, slice.status),
  })).filter((slice) => slice.count > 0)
  const total = slices.reduce((sum, slice) => sum + slice.count, 0)
  const hours = hourBuckets(schedule, timeZone)
  const doctorRows = (doctors ?? []).map((row) => ({ row, parts: doctorParts(row) }))
  const busiestHour = Math.max(1, ...hours.map((bucket) => bucket.count))
  const busiestDoctor = Math.max(1, ...doctorRows.map(({ row }) => row.appointments))
  const hourLegend = legendKeys(hours)
  const doctorLegend = legendKeys(doctorRows)

  let cursor = 0
  const gradient =
    total === 0
      ? 'var(--color-surface-sunken)'
      : `conic-gradient(${slices
          .map((slice) => {
            const start = cursor
            cursor += slice.count / total
            return `${slice.color} ${start * 360}deg ${cursor * 360}deg`
          })
          .join(', ')})`

  return (
    <div className={styles.visuals}>
      <figure className={styles.chartCard}>
        <figcaption className={styles.chartTitle}>{t('charts.mixTitle')}</figcaption>
        {total === 0 ? (
          <p className={styles.muted}>{t('charts.mixEmpty')}</p>
        ) : (
          <div className={styles.mix}>
            <div
              className={styles.donut}
              style={{ background: gradient }}
              aria-hidden="true"
            >
              <div className={styles.donutHole}>
                <span className={cx(styles.donutValue, 'tabular')}>{number.integer(summary.appointments)}</span>
                <span className={styles.donutCaption}>{t('charts.mixTotal')}</span>
              </div>
            </div>
            <ul className={styles.legend}>
              {slices.map((slice) => (
                <li key={slice.key} className={cx(styles.legendItem, styles[slice.tone])}>
                  <span className={styles.swatch} aria-hidden="true" />
                  <span>{slice.label}</span>
                  <span className={cx(styles.legendCount, 'tabular')}>{number.integer(slice.count)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
        <table className="visually-hidden">
          <caption>{t('charts.mixCaption')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('charts.status')}</th>
              <th scope="col">{t('charts.count')}</th>
            </tr>
          </thead>
          <tbody>
            {STATUS_SLICES.map((slice) => (
              <tr key={slice.key}>
                <th scope="row">{sliceLabel(slice.key, slice.status)}</th>
                <td>{number.integer(countOf(summary, slice.key))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </figure>

      <figure className={styles.chartCard}>
        <figcaption className={styles.chartTitle}>{t('charts.hoursTitle')}</figcaption>
        {hours.length === 0 ? (
          <p className={styles.muted}>{t('charts.hoursEmpty')}</p>
        ) : (
          <>
            <div className={styles.histogram} aria-hidden="true">
              {hours.map((bucket) => (
                <div
                  key={bucket.hour}
                  className={styles.column}
                  title={paint(bucket.parts)
                    .map((part) => `${partLabel(part.key)} ${number.integer(part.count)}`)
                    .join(', ')}
                >
                  <span className={styles.columnPlot}>
                    {bucket.count > 0 && (
                      <span
                        className={styles.columnBar}
                        style={{ '--share': bucket.count / busiestHour } as CSSProperties}
                      >
                        {paint(bucket.parts).map((part) => (
                          <span
                            key={part.key}
                            className={styles.columnSegment}
                            style={{ '--part': part.weight, '--segment': part.color } as CSSProperties}
                          />
                        ))}
                      </span>
                    )}
                  </span>
                  <span className={styles.columnLabel}>{hourLabel(bucket.hour, i18n.language)}</span>
                </div>
              ))}
            </div>
            {hourLegend.length > 0 && (
              <ul className={styles.chartLegend}>
                {hourLegend.map((key) => (
                  <li key={key} className={cx(styles.chartLegendItem, styles[toneOf(key)])}>
                    <span className={styles.swatch} aria-hidden="true" />
                    <span>{partLabel(key)}</span>
                  </li>
                ))}
              </ul>
            )}
            {hoursPartial && <p className={styles.muted}>{t('schedule.truncated')}</p>}
          </>
        )}
        <table className="visually-hidden">
          <caption>{t('charts.hoursCaption')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('charts.hour')}</th>
              <th scope="col">{t('charts.count')}</th>
              <th scope="col">{tCommon('appointmentStatus.NO_SHOW')}</th>
            </tr>
          </thead>
          <tbody>
            {hours.map((bucket) => (
              <tr key={bucket.hour}>
                <th scope="row">{hourLabel(bucket.hour, i18n.language)}</th>
                <td>{number.integer(bucket.count)}</td>
                <td>{number.integer(bucket.missed)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </figure>

      {doctors && doctors.length > 0 && (
        <figure className={cx(styles.chartCard, styles.chartWide)}>
          <figcaption className={styles.chartTitle}>{t('charts.doctorsTitle')}</figcaption>
          <ul className={styles.bars} aria-hidden="true">
            {doctorRows.map(({ row, parts }) => (
              <li key={row.doctor.id} className={styles.barRow}>
                <span className={styles.barName}>{personName(row.doctor)}</span>
                <span className={styles.barTrack}>
                  <span
                    className={styles.barFill}
                    style={{ '--share': row.appointments / busiestDoctor } as CSSProperties}
                  >
                    {paint(parts).map((part) => (
                      <span
                        key={part.key}
                        className={styles.barSegment}
                        style={{ '--part': part.weight, '--segment': part.color } as CSSProperties}
                      />
                    ))}
                  </span>
                </span>
                <span className={cx(styles.barValue, 'tabular')}>{number.integer(row.appointments)}</span>
              </li>
            ))}
          </ul>
          {doctorLegend.length > 0 && (
            <ul className={styles.chartLegend}>
              {doctorLegend.map((key) => (
                <li key={key} className={cx(styles.chartLegendItem, styles[toneOf(key)])}>
                  <span className={styles.swatch} aria-hidden="true" />
                  <span>{partLabel(key)}</span>
                </li>
              ))}
            </ul>
          )}
          <table className="visually-hidden">
            <caption>{t('charts.doctorsCaption')}</caption>
            <thead>
            <tr>
              <th scope="col">{t('charts.doctor')}</th>
              <th scope="col">{t('charts.count')}</th>
              <th scope="col">{tCommon('appointmentStatus.NO_SHOW')}</th>
            </tr>
          </thead>
          <tbody>
            {doctorRows.map(({ row }) => (
              <tr key={row.doctor.id}>
                <th scope="row">{personName(row.doctor)}</th>
                <td>{number.integer(row.appointments)}</td>
                <td>{number.integer(row.noShows)}</td>
              </tr>
            ))}
          </tbody>
          </table>
        </figure>
      )}
    </div>
  )
}
