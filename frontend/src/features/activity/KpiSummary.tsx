import { useTranslation } from 'react-i18next'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { KpiGrid, KpiTile, type KpiComparison, type KpiTone } from '../../shared/ui/KpiTile'
import type { KpiData, KpiFigures } from './api'

export type KpiMetric =
  | 'appointments'
  | 'completed'
  | 'completionRate'
  | 'noShowRate'
  | 'cancellationRate'
  | 'newPatients'
  | 'activePatients'
  | 'consultations'

/** Whether a rise is good news; volume metrics count as good, lost appointments as bad. */
const HIGHER_IS_BETTER: Record<KpiMetric, boolean> = {
  appointments: true,
  completed: true,
  completionRate: true,
  noShowRate: false,
  cancellationRate: false,
  newPatients: true,
  activePatients: true,
  consultations: true,
}

const RATES = new Set<KpiMetric>(['completionRate', 'noShowRate', 'cancellationRate'])

/** Matches the overview pulse: the same status fills, not a second palette. */
const TONE: Record<KpiMetric, KpiTone> = {
  appointments: 'scheduled',
  completed: 'completed',
  completionRate: 'completed',
  noShowRate: 'missed',
  cancellationRate: 'cancelled',
  newPatients: 'accent',
  activePatients: 'accent',
  consultations: 'consultation',
}

function valueOf(figures: KpiFigures, metric: KpiMetric): number | null {
  return metric === 'completed' ? figures.appointmentsByStatus.COMPLETED : figures[metric]
}

/** KPI tiles of a period, each compared with the equally long period before it (design-system.md §11.8). */
export function KpiSummary({ data, metrics }: { data: KpiData; metrics: readonly KpiMetric[] }) {
  const { t } = useTranslation('activity')
  const number = useNumberFormat()

  const comparisonOf = (metric: KpiMetric): KpiComparison | undefined => {
    const current = valueOf(data.current, metric)
    const previous = valueOf(data.previous, metric)
    if (current === null || previous === null) return undefined
    let change: number
    let text: string
    if (RATES.has(metric)) {
      change = Math.round((current - previous) * 100)
      text = t('comparison.points', { value: number.signedInteger(change) })
    } else {
      if (previous === 0) return undefined
      change = (current - previous) / previous
      text = t('comparison.change', { value: number.signedPercent(change) })
      if (Math.round(change * 100) === 0) change = 0
    }
    const direction = change > 0 ? 'up' : change < 0 ? 'down' : 'flat'
    const better = HIGHER_IS_BETTER[metric] ? direction === 'up' : direction === 'down'
    return {
      text,
      direction,
      tone: direction === 'flat' ? 'neutral' : better ? 'positive' : 'negative',
    }
  }

  return (
    <KpiGrid label={t('kpis.label')}>
      {metrics.map((metric) => {
        const value = valueOf(data.current, metric)
        const formatted = value === null ? '—' : RATES.has(metric) ? number.percent(value) : number.integer(value)
        return (
          <KpiTile
            key={metric}
            label={t(`kpis.${metric}`)}
            value={formatted}
            comparison={comparisonOf(metric)}
            tone={TONE[metric]}
          />
        )
      })}
    </KpiGrid>
  )
}
