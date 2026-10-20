import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { cx } from '../../shared/lib/cx'
import type { KpiData, TrendPoint } from './api'
import styles from './Activity.module.css'

const SEGMENTS = [
  { key: 'completed', className: styles.segmentCompleted },
  { key: 'other', className: styles.segmentOther },
  { key: 'noShows', className: styles.segmentNoShows },
  { key: 'cancelled', className: styles.segmentCancelled },
] as const

type Segment = (typeof SEGMENTS)[number]['key']

function countOf(point: TrendPoint, segment: Segment): number {
  return segment === 'other' ? point.appointments - point.completed - point.noShows - point.cancelled : point[segment]
}

/**
 * Appointments per day or month, stacked by outcome. The bars are decorative for screen readers, which get the same
 * figures as a table.
 */
export function TrendChart({ data }: { data: KpiData }) {
  const { t } = useTranslation('activity')
  const format = useDateTimeFormat('UTC')
  const number = useNumberFormat()
  const label = (date: string) => (data.trendBucket === 'DAY' ? format.weekdayDate(date) : format.monthYear(date))
  const busiest = Math.max(1, ...data.trend.map((point) => point.appointments))
  const first = data.trend[0]
  const last = data.trend[data.trend.length - 1]

  return (
    <div>
      <div className={styles.chart} aria-hidden="true">
        {data.trend.map((point) => (
          <div
            key={point.date}
            className={styles.bar}
            style={{ '--share': point.appointments / busiest } as CSSProperties}
            title={`${label(point.date)}: ${number.integer(point.appointments)}`}
          >
            {SEGMENTS.map((segment) => (
              <span
                key={segment.key}
                className={cx(styles.segment, segment.className)}
                style={{ '--count': countOf(point, segment.key) } as CSSProperties}
              />
            ))}
          </div>
        ))}
      </div>
      {first && last && (
        <div className={styles.axis} aria-hidden="true">
          <span>{label(first.date)}</span>
          <span>{label(last.date)}</span>
        </div>
      )}
      <ul className={styles.legend} aria-hidden="true">
        {SEGMENTS.map((segment) => (
          <li key={segment.key} className={styles.legendItem}>
            <span className={cx(styles.swatch, segment.className)} />
            {t(`trend.${segment.key}`)}
          </li>
        ))}
      </ul>
      <div className="visually-hidden">
        <table>
          <caption>{t('trend.caption')}</caption>
          <thead>
            <tr>
              <th scope="col">{t(data.trendBucket === 'DAY' ? 'trend.day' : 'trend.month')}</th>
              <th scope="col">{t('kpis.appointments')}</th>
              {SEGMENTS.map((segment) => (
                <th key={segment.key} scope="col">
                  {t(`trend.${segment.key}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.trend.map((point) => (
              <tr key={point.date}>
                <th scope="row">{label(point.date)}</th>
                <td>{number.integer(point.appointments)}</td>
                {SEGMENTS.map((segment) => (
                  <td key={segment.key}>{number.integer(countOf(point, segment.key))}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
