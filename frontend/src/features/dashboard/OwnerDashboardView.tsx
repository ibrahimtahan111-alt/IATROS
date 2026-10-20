import { useTranslation } from 'react-i18next'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { KpiGrid, KpiTile } from '../../shared/ui/KpiTile'
import { Page, Section } from '../../shared/ui/Page'
import { useSessionUser } from '../auth/session'
import { personName } from '../calendar/api'
import { useOwnerDashboard, type OwnerDashboard } from './api'
import { DashboardSkeleton, Greeting, PerformanceSection, RecentActivitySection, SectionFooter } from './DashboardParts'
import { useGreeting } from './useGreeting'
import { TodaySchedule } from './TodaySchedule'
import styles from './Dashboard.module.css'

/** The clinic today, its doctors, period KPIs, and recent activity (ui-ux.md §7). */
export function OwnerDashboardView() {
  const { t } = useTranslation()
  const dashboard = useOwnerDashboard()

  return (
    <Page width="full">
      <ContextBar title={t('nav.overview')} />
      {dashboard.isError ? (
        <ErrorState error={dashboard.error} onRetry={() => void dashboard.refetch()} retrying={dashboard.isFetching} />
      ) : !dashboard.data ? (
        <DashboardSkeleton />
      ) : (
        <OwnerDashboardContent data={dashboard.data} />
      )}
    </Page>
  )
}

function OwnerDashboardContent({ data }: { data: OwnerDashboard }) {
  const { t } = useTranslation('dashboard')
  const user = useSessionUser()
  const greeting = useGreeting(data.timeZone)
  const number = useNumberFormat()
  const dayView = `/calendar?view=day&date=${data.today}`

  return (
    <>
      <Greeting title={greeting(user.firstName)} today={data.today} timeZone={data.timeZone} />
      <div className={styles.tiles}>
        <KpiGrid label={t('today.label')}>
          <KpiTile label={t('today.appointments')} value={number.integer(data.summary.appointments)} to={dayView} />
          <KpiTile label={t('today.completed')} value={number.integer(data.summary.completed)} />
          <KpiTile label={t('today.waiting')} value={number.integer(data.summary.waiting)} />
          <KpiTile label={t('today.noShows')} value={number.integer(data.summary.noShows)} />
          <KpiTile label={t('owner.totalPatients')} value={number.integer(data.activePatients)} to="/patients" />
          <KpiTile label={t('owner.activeDoctors')} value={number.integer(data.activeDoctors)} to="/team" />
        </KpiGrid>
      </div>

      <div className={styles.columns}>
        <Section title={t('schedule.title')} description={t('schedule.clinicDescription')}>
          {data.schedule.length === 0 ? (
            <p className={styles.muted}>{t('schedule.empty')}</p>
          ) : (
            <TodaySchedule items={data.schedule} today={data.today} timeZone={data.timeZone} showDoctor />
          )}
          {data.scheduleTruncated && <p className={styles.muted}>{t('schedule.truncated')}</p>}
          <SectionFooter>
            <ButtonLink size="sm" to={dayView}>
              {t('schedule.openCalendar')}
            </ButtonLink>
          </SectionFooter>
        </Section>

        <Section title={t('owner.doctorsTitle')}>
          {data.doctors.length === 0 ? (
            <p className={styles.muted}>{t('owner.noDoctors')}</p>
          ) : (
            <ul className={styles.list}>
              {data.doctors.map((row) => (
                <li key={row.doctor.id} className={styles.listItem}>
                  <span>{personName(row.doctor)}</span>
                  <span className={`${styles.figures} tabular`}>
                    <span>
                      {t('owner.doctorAppointments', {
                        count: row.appointments,
                      })}
                    </span>
                    {row.completed > 0 && <span>{t('owner.doctorCompleted', { count: row.completed })}</span>}
                    {row.inProgress > 0 && <span>{t('owner.doctorInProgress', { count: row.inProgress })}</span>}
                    {row.remaining > 0 && <span>{t('owner.doctorRemaining', { count: row.remaining })}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>

      <PerformanceSection
        title={t('performance.clinicTitle')}
        doctorId={null}
        metrics={['appointments', 'completionRate', 'noShowRate', 'newPatients']}
      />
      <RecentActivitySection title={t('activity.clinicTitle')} items={data.recentActivity} />
    </>
  )
}
