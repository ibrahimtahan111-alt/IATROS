import { addDays, todayIn } from '../../shared/lib/localDates'
import { KPI_PERIODS, type KpiPeriod } from './api'

export const DEFAULT_PERIOD: KpiPeriod = 'month'

export function isKpiPeriod(value: string | null): value is KpiPeriod {
  return value !== null && Object.hasOwn(KPI_PERIODS, value)
}

/** The clinic-local dates of a preset: the last N days up to and including today. */
export function periodRange(period: KpiPeriod, timeZone: string | null | undefined): { from: string; to: string } {
  const to = todayIn(timeZone)
  return { from: addDays(to, 1 - KPI_PERIODS[period]), to }
}
