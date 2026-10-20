import { History } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { navLabelKey, NAV_ITEMS } from '../../app/navigation'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { Button } from '../../shared/ui/Button'
import { ContextBar } from '../../shared/ui/ContextBar'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page, Section } from '../../shared/ui/Page'
import { Pagination } from '../../shared/ui/Pagination'
import { Select } from '../../shared/ui/Select'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { Table, Td, Th } from '../../shared/ui/Table'
import { useSessionUser } from '../auth/session'
import { personName } from '../calendar/api'
import { doctorName, useDoctorChoices } from '../doctors/api'
import { ActivityFeed } from './ActivityFeed'
import {
  ACTIVITY_CATEGORIES,
  useActivity,
  useKpis,
  type ActivityCategory,
  type DoctorFigures,
  type KpiData,
} from './api'
import { KpiSummary, type KpiMetric } from './KpiSummary'
import { PeriodControl } from './PeriodControl'
import { DEFAULT_PERIOD, isKpiPeriod, periodRange } from './period'
import { TrendChart } from './TrendChart'
import styles from './Activity.module.css'

const ALL = 'ALL'
const PAGE_SIZE = 20
const METRICS: readonly KpiMetric[] = [
  'appointments',
  'completed',
  'completionRate',
  'noShowRate',
  'cancellationRate',
  'newPatients',
  'activePatients',
  'consultations',
]

/**
 * KPIs and clinic activity (ui-ux.md §22). Owners see the clinic or one doctor; doctors only themselves, which the
 * server enforces (D-039). Period, doctor, activity type, and page are kept in the URL.
 */
export function ActivityPage() {
  const { t } = useTranslation('activity')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const [searchParams, setSearchParams] = useSearchParams()
  const isOwner = user.role === 'OWNER'

  const periodParam = searchParams.get('period')
  const period = isKpiPeriod(periodParam) ? periodParam : DEFAULT_PERIOD
  const { from, to } = periodRange(period, user.clinic.timeZone)
  const doctorFilter = isOwner ? searchParams.get('doctor') : null
  const typeParam = searchParams.get('type')
  const category = ACTIVITY_CATEGORIES.includes(typeParam as ActivityCategory) ? (typeParam as ActivityCategory) : null
  const page = Math.max(0, Number(searchParams.get('page')) || 0)

  const kpiDoctor = isOwner ? doctorFilter : user.doctorId
  const kpis = useKpis(kpiDoctor, from, to, isOwner || Boolean(user.doctorId))
  const activity = useActivity({
    from,
    to,
    categories: category ? [category] : [],
    doctorId: doctorFilter,
    page,
    size: PAGE_SIZE,
  })
  const doctors = useDoctorChoices(isOwner)

  const update = (changes: Record<string, string | null>) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        if (!('page' in changes)) next.delete('page')
        return next
      },
      { replace: true },
    )

  const navItem = NAV_ITEMS.find((item) => item.path === '/activity')!
  const title = tCommon(navLabelKey(navItem, user.role) as 'nav.activity')

  return (
    <Page width="full">
      <ContextBar
        title={title}
        scope={
          <div className={styles.toolbar}>
            <PeriodControl
              value={period}
              onValueChange={(next) => update({ period: next === DEFAULT_PERIOD ? null : next })}
            />
            {isOwner && (
              <div className={styles.filter}>
                <Select
                  aria-label={t('filters.doctor')}
                  value={doctorFilter ?? ALL}
                  onValueChange={(value) => update({ doctor: value === ALL ? null : value })}
                  options={[
                    { value: ALL, label: t('filters.allDoctors') },
                    ...(doctors.data ?? []).map((doctor) => ({
                      value: doctor.id,
                      label: doctorName(doctor),
                    })),
                  ]}
                />
              </div>
            )}
          </div>
        }
      />

      <KpiSections kpis={kpis} showDoctors={isOwner && !doctorFilter} onDoctor={(id) => update({ doctor: id })} />

      <Section
        title={t('feed.title')}
        description={t('feed.description')}
        action={
          <div className={styles.filter}>
            <Select
              aria-label={t('filters.type')}
              value={category ?? ALL}
              onValueChange={(value) => update({ type: value === ALL ? null : value })}
              options={[
                { value: ALL, label: t('filters.allTypes') },
                ...ACTIVITY_CATEGORIES.map((value) => ({
                  value,
                  label: t(`category.${value}`),
                })),
              ]}
            />
          </div>
        }
      >
        {activity.isError ? (
          <ErrorState error={activity.error} onRetry={() => void activity.refetch()} retrying={activity.isFetching} />
        ) : !activity.data ? (
          <LoadingRegion label={tCommon('states.loading')}>
            <div className={styles.stack}>
              {Array.from({ length: 5 }, (_, index) => (
                <Skeleton key={index} height="var(--space-10)" />
              ))}
            </div>
          </LoadingRegion>
        ) : activity.data.totalElements === 0 ? (
          <EmptyState
            icon={<History />}
            title={t('feed.empty')}
            description={t('feed.emptyDescription')}
            action={category && <Button onClick={() => update({ type: null })}>{t('filters.clear')}</Button>}
          />
        ) : (
          <div aria-busy={activity.isFetching || undefined}>
            <ActivityFeed items={activity.data.content} />
            <Pagination
              page={activity.data.page}
              size={activity.data.size}
              totalElements={activity.data.totalElements}
              onPageChange={(next) => update({ page: next ? String(next) : null })}
            />
          </div>
        )}
      </Section>
    </Page>
  )
}

interface KpiSectionsProps {
  kpis: ReturnType<typeof useKpis>
  showDoctors: boolean
  onDoctor: (doctorId: string) => void
}

function KpiSections({ kpis, showDoctors, onDoctor }: KpiSectionsProps) {
  const { t } = useTranslation('activity')
  const { t: tCommon } = useTranslation()
  const format = useDateTimeFormat('UTC')

  if (kpis.isError) {
    return (
      <Section title={t('kpis.title')}>
        <ErrorState error={kpis.error} onRetry={() => void kpis.refetch()} retrying={kpis.isFetching} />
      </Section>
    )
  }
  const data = kpis.data
  if (!data) {
    return (
      <Section title={t('kpis.title')}>
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Section>
    )
  }
  return (
    <>
      <Section
        title={data.doctor ? t('kpis.titleFor', { name: personName(data.doctor) }) : t('kpis.title')}
        description={t('kpis.description', {
          range: format.localDateRange(data.from, data.to),
          previous: format.localDateRange(data.previousFrom, data.previousTo),
        })}
      >
        <div aria-busy={kpis.isFetching || undefined}>
          <KpiSummary data={data} metrics={METRICS} />
        </div>
      </Section>
      <Section title={t('trend.title')}>
        {data.current.appointments === 0 ? (
          <p className={styles.muted}>{t('trend.empty')}</p>
        ) : (
          <TrendChart data={data} />
        )}
      </Section>
      {showDoctors && data.doctors.length > 0 && (
        <Section title={t('doctors.title')} description={t('doctors.description')}>
          <DoctorTable data={data} onDoctor={onDoctor} />
        </Section>
      )}
    </>
  )
}

function DoctorTable({ data, onDoctor }: { data: KpiData; onDoctor: (doctorId: string) => void }) {
  const { t } = useTranslation('activity')
  const number = useNumberFormat()
  const rate = (row: DoctorFigures) => (row.completionRate === null ? '—' : number.percent(row.completionRate))
  return (
    <Table caption={t('doctors.caption')}>
      <thead>
        <tr>
          <Th>{t('doctors.doctor')}</Th>
          <Th>{t('kpis.appointments')}</Th>
          <Th>{t('kpis.completed')}</Th>
          <Th>{t('doctors.noShows')}</Th>
          <Th>{t('doctors.cancelled')}</Th>
          <Th>{t('kpis.completionRate')}</Th>
          <Th>{t('kpis.consultations')}</Th>
        </tr>
      </thead>
      <tbody>
        {data.doctors.map((row) => (
          <tr key={row.doctor.id}>
            <Td primary>
              <button type="button" className={styles.linkButton} onClick={() => onDoctor(row.doctor.id)}>
                {personName(row.doctor)}
              </button>
            </Td>
            <Td className="tabular">{number.integer(row.appointments)}</Td>
            <Td className="tabular">{number.integer(row.completed)}</Td>
            <Td className="tabular">{number.integer(row.noShows)}</Td>
            <Td className="tabular">{number.integer(row.cancelled)}</Td>
            <Td className="tabular">{rate(row)}</Td>
            <Td className="tabular">{number.integer(row.consultations)}</Td>
          </tr>
        ))}
      </tbody>
    </Table>
  )
}
