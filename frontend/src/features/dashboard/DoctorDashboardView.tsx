import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { ModuleSwitch } from '../../app/ModuleSwitch'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { KpiGrid, KpiTile } from '../../shared/ui/KpiTile'
import { Page, Section } from '../../shared/ui/Page'
import { personName } from '../calendar/api'
import { useDoctorDashboard, type DoctorDashboard } from './api'
import { DashboardSkeleton, Greeting, PerformanceSection, RecentActivitySection, SectionFooter } from './DashboardParts'
import { useGreeting } from './useGreeting'
import { TodaySchedule } from './TodaySchedule'
import styles from './Dashboard.module.css'

/** A doctor's own day: schedule with clinical alerts, patients, own KPIs and activity (ui-ux.md §8). */
export function DoctorDashboardView() {
  const { t } = useTranslation()
  const dashboard = useDoctorDashboard()

  return (
    <Page width="full">
      <ContextBar title={t('nav.myDay')} scope={<ModuleSwitch current="overview" />} />
      {dashboard.isError ? (
        <ErrorState error={dashboard.error} onRetry={() => void dashboard.refetch()} retrying={dashboard.isFetching} />
      ) : !dashboard.data ? (
        <DashboardSkeleton />
      ) : (
        <DoctorDashboardContent data={dashboard.data} />
      )}
    </Page>
  )
}

function DoctorDashboardContent({ data }: { data: DoctorDashboard }) {
  const { t } = useTranslation('dashboard')
  const greeting = useGreeting(data.timeZone)
  const number = useNumberFormat()
  const format = useDateTimeFormat(data.timeZone)
  const dayView = `/calendar?view=day&date=${data.today}`

  return (
    <>
      <Greeting
        title={greeting(t('greeting.doctorName', { lastName: data.doctor.lastName }))}
        today={data.today}
        timeZone={data.timeZone}
      />
      <div className={styles.tiles}>
        <KpiGrid label={t('today.label')}>
          <KpiTile tone="accent" label={t('doctor.todayPatients')} value={number.integer(data.summary.patients)} />
          <KpiTile
            tone="scheduled"
            label={t('today.appointments')}
            value={number.integer(data.summary.appointments)}
            to={dayView}
          />
          <KpiTile tone="consultation" label={t('doctor.upcoming')} value={number.integer(data.upcomingAppointments)} to="/calendar" />
          <KpiTile tone="completed" label={t('doctor.completedToday')} value={number.integer(data.summary.completed)} />
        </KpiGrid>
      </div>

      <div className={styles.columns}>
        <Section title={t('schedule.title')} description={t('schedule.doctorDescription')}>
          {data.schedule.length === 0 ? (
            <p className={styles.muted}>{t('schedule.empty')}</p>
          ) : (
            <TodaySchedule items={data.schedule} today={data.today} timeZone={data.timeZone} showDoctor={false} />
          )}
          <SectionFooter>
            <ButtonLink size="sm" to={dayView}>
              {t('schedule.openCalendar')}
            </ButtonLink>
          </SectionFooter>
        </Section>

        <Section
          title={t('doctor.patientsTitle')}
          description={t('doctor.patientsSummary', {
            count: data.patients,
            primary: data.primaryPatients,
          })}
        >
          {data.recentPatients.length === 0 ? (
            <p className={styles.muted}>{t('doctor.noRecentPatients')}</p>
          ) : (
            <ul className={styles.list} aria-label={t('doctor.recentPatients')}>
              {data.recentPatients.map((row) => (
                <li key={row.patient.id} className={styles.listItem}>
                  <Link className={styles.scheduleLink} to={`/patients/${row.patient.id}`}>
                    {personName(row.patient)}
                  </Link>
                  <span className={`${styles.figures} tabular`}>
                    {t('doctor.lastVisit', {
                      date: format.date(row.lastVisitAt),
                    })}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <SectionFooter>
            <ButtonLink size="sm" to="/patients">
              {t('doctor.allPatients')}
            </ButtonLink>
          </SectionFooter>
        </Section>
      </div>

      <PerformanceSection
        title={t('performance.doctorTitle')}
        doctorId={data.doctor.id}
        metrics={['appointments', 'completionRate', 'noShowRate', 'consultations']}
      />
      <RecentActivitySection title={t('activity.doctorTitle')} items={data.recentActivity} />
    </>
  )
}
