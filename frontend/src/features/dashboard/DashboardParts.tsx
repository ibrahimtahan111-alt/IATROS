import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { ActivityFeed } from '../activity/ActivityFeed'
import { useKpis, type ActivityItem, type KpiPeriod } from '../activity/api'
import { KpiSummary, type KpiMetric } from '../activity/KpiSummary'
import { PeriodControl } from '../activity/PeriodControl'
import { DEFAULT_PERIOD, periodRange } from '../activity/period'
import { TrendChart } from '../activity/TrendChart'
import { useSessionUser } from '../auth/session'
import styles from './Dashboard.module.css'

export function Greeting({ title, today, timeZone }: { title: string; today: string; timeZone: string }) {
  const format = useDateTimeFormat(timeZone)
  return (
    <div className={styles.greeting}>
      <p className={styles.greetingTitle}>{title}</p>
      <p className={styles.muted}>{format.longDate(today)}</p>
    </div>
  )
}

export function SectionFooter({ children }: { children: ReactNode }) {
  return <div className={styles.sectionFooter}>{children}</div>
}

export function DashboardSkeleton() {
  const { t } = useTranslation()
  return (
    <LoadingRegion label={t('states.loading')}>
      <div className={styles.stack}>
        <Skeleton height="var(--space-12)" width="40%" />
        <Skeleton height="var(--space-16)" />
        <Skeleton height="calc(var(--space-16) * 3)" />
      </div>
    </LoadingRegion>
  )
}

interface PerformanceSectionProps {
  title: string
  /** Null for clinic KPIs (owners). */
  doctorId: string | null
  metrics: readonly KpiMetric[]
}

/** Period KPIs with comparison and trend; the full breakdown lives on the activity page. */
export function PerformanceSection({ title, doctorId, metrics }: PerformanceSectionProps) {
  const { t } = useTranslation('dashboard')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const [period, setPeriod] = useState<KpiPeriod>(DEFAULT_PERIOD)
  const { from, to } = periodRange(period, user.clinic.timeZone)
  const kpis = useKpis(doctorId, from, to)

  return (
    <Section title={title} action={<PeriodControl value={period} onValueChange={setPeriod} />}>
      {kpis.isError ? (
        <ErrorState error={kpis.error} onRetry={() => void kpis.refetch()} retrying={kpis.isFetching} />
      ) : !kpis.data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      ) : (
        <div className={styles.stack} aria-busy={kpis.isFetching || undefined}>
          <KpiSummary data={kpis.data} metrics={metrics} />
          {kpis.data.current.appointments > 0 && <TrendChart data={kpis.data} />}
          <SectionFooter>
            <ButtonLink size="sm" to={period === DEFAULT_PERIOD ? '/activity' : `/activity?period=${period}`}>
              {t('performance.more')}
            </ButtonLink>
          </SectionFooter>
        </div>
      )}
    </Section>
  )
}

export function RecentActivitySection({ title, items }: { title: string; items: readonly ActivityItem[] }) {
  const { t } = useTranslation('dashboard')
  return (
    <Section title={title}>
      {items.length === 0 ? <p className={styles.muted}>{t('activity.empty')}</p> : <ActivityFeed items={items} />}
      <SectionFooter>
        <ButtonLink size="sm" to="/activity">
          {t('activity.more')}
        </ButtonLink>
      </SectionFooter>
    </Section>
  )
}
