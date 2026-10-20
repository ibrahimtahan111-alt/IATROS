import { useTranslation } from 'react-i18next'
import { SegmentedControl } from '../../shared/ui/SegmentedControl'
import { KPI_PERIODS, type KpiPeriod } from './api'

const PERIODS = Object.keys(KPI_PERIODS) as KpiPeriod[]

export function PeriodControl({
  value,
  onValueChange,
}: {
  value: KpiPeriod
  onValueChange: (period: KpiPeriod) => void
}) {
  const { t } = useTranslation('activity')
  return (
    <SegmentedControl
      label={t('period.label')}
      value={value}
      onValueChange={onValueChange}
      options={PERIODS.map((period) => ({
        value: period,
        label: t(`period.${period}`),
      }))}
    />
  )
}
