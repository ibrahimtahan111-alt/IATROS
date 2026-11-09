import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { ModuleSwitch } from '../../app/ModuleSwitch'
import { useNumberFormat } from '../../shared/hooks/useNumberFormat'
import { cx } from '../../shared/lib/cx'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page, Section } from '../../shared/ui/Page'
import { useSessionUser } from '../auth/session'
import { personName } from '../calendar/api'
import { useOwnerDashboard, type OwnerDashboard, type ScheduleItem } from './api'
import { DashboardSkeleton, Greeting, SectionFooter } from './DashboardParts'
import { useGreeting } from './useGreeting'
import { TodaySchedule } from './TodaySchedule'
import styles from './Dashboard.module.css'

/** How many not-yet-started appointments to list before sending the owner to the calendar. */
const COMING_UP_LIMIT = 6

/** The clinic today. Activity is the next module, reached from the switch (ui-ux.md §7). */
export function OwnerDashboardView() {
  const { t } = useTranslation()
  const dashboard = useOwnerDashboard()

  return (
    <Page width="full">
      <ContextBar title={t('nav.overview')} scope={<ModuleSwitch current="overview" />} />
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
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const greeting = useGreeting(data.timeZone)
  const number = useNumberFormat()
  const dayView = `/calendar?view=day&date=${data.today}`
  const waiting = data.schedule.filter((item) => item.status === 'WAITING')
  const consulting = data.schedule.filter((item) => item.status === 'IN_CONSULTATION')
  const comingUp = data.schedule.filter((item) => item.status === 'SCHEDULED' || item.status === 'CONFIRMED')

  return (
    <>
      <Greeting title={greeting(user.firstName)} today={data.today} timeZone={data.timeZone} />

      <div className={styles.pulse} role="group" aria-label={t('today.label')}>
        <PulseLink
          tone="waiting"
          label={tCommon('appointmentStatus.WAITING')}
          value={number.integer(data.summary.waiting)}
          to={dayView}
        />
        <PulseLink
          tone="consult"
          label={tCommon('appointmentStatus.IN_CONSULTATION')}
          value={number.integer(data.summary.inConsultation)}
          to={dayView}
        />
        <PulseLink
          tone="next"
          label={t('today.remaining')}
          value={number.integer(data.summary.remaining)}
          to={dayView}
        />
        <PulseLink
          tone="done"
          label={tCommon('appointmentStatus.COMPLETED')}
          value={number.integer(data.summary.completed)}
          to={dayView}
        />
        {data.summary.noShows > 0 && (
          <PulseLink
            tone="missed"
            label={tCommon('appointmentStatus.NO_SHOW')}
            value={number.integer(data.summary.noShows)}
            to={dayView}
          />
        )}
      </div>

      <div className={styles.columns}>
        <Section title={t('owner.waitingRoom')}>
          <StatusList items={waiting} today={data.today} timeZone={data.timeZone} empty={t('owner.waitingEmpty')} />
        </Section>
        <Section title={t('owner.consultingTitle')}>
          <StatusList
            items={consulting}
            today={data.today}
            timeZone={data.timeZone}
            empty={t('owner.consultingEmpty')}
          />
        </Section>
      </div>

      <div className={styles.columns}>
        <Section title={t('owner.doctorsTitle')}>
          {data.doctors.length === 0 ? (
            <p className={styles.muted}>{t('owner.noDoctors')}</p>
          ) : (
            <ul className={styles.doctors}>
              {data.doctors.map((row) => (
                <DoctorLoad key={row.doctor.id} row={row} today={data.today} />
              ))}
            </ul>
          )}
        </Section>

        <Section title={t('owner.comingTitle')} description={t('owner.comingDescription')}>
          {comingUp.length === 0 ? (
            <p className={styles.muted}>{t('owner.comingEmpty')}</p>
          ) : (
            <TodaySchedule
              items={comingUp.slice(0, COMING_UP_LIMIT)}
              today={data.today}
              timeZone={data.timeZone}
              showDoctor
            />
          )}
          {(data.scheduleTruncated || comingUp.length > COMING_UP_LIMIT) && (
            <p className={styles.muted}>{t('schedule.truncated')}</p>
          )}
          <SectionFooter>
            <ButtonLink size="sm" to={dayView}>
              {t('schedule.openCalendar')}
            </ButtonLink>
          </SectionFooter>
        </Section>
      </div>
    </>
  )
}

function PulseLink({
  tone,
  label,
  value,
  to,
}: {
  tone: 'waiting' | 'consult' | 'next' | 'done' | 'missed'
  label: string
  value: string
  to: string
}) {
  return (
    <Link to={to} className={cx(styles.pulseTile, styles[tone])}>
      <span className={styles.pulseLabel}>{label}</span>
      <span className={cx(styles.pulseValue, 'tabular')}>{value}</span>
    </Link>
  )
}

function StatusList({
  items,
  today,
  timeZone,
  empty,
}: {
  items: readonly ScheduleItem[]
  today: string
  timeZone: string
  empty: string
}) {
  if (items.length === 0) {
    return <p className={styles.muted}>{empty}</p>
  }
  return <TodaySchedule items={items} today={today} timeZone={timeZone} showDoctor />
}

function DoctorLoad({ row, today }: { row: OwnerDashboard['doctors'][number]; today: string }) {
  const { t } = useTranslation('dashboard')
  const segments = [
    { key: 'done', count: row.completed, tone: styles.segDone },
    { key: 'now', count: row.inProgress, tone: styles.segNow },
    { key: 'next', count: row.remaining, tone: styles.segNext },
    { key: 'missed', count: row.noShows, tone: styles.segMissed },
  ].filter((segment) => segment.count > 0)

  return (
    <li className={styles.doctor}>
      <div className={styles.doctorHead}>
        <Link className={styles.scheduleLink} to={`/calendar?view=day&date=${today}&doctor=${row.doctor.id}`}>
          {personName(row.doctor)}
        </Link>
        <span className={`${styles.figures} tabular`}>{t('owner.doctorAppointments', { count: row.appointments })}</span>
      </div>
      <div className={styles.load} aria-hidden="true">
        {segments.map((segment) => (
          <span key={segment.key} className={cx(styles.segment, segment.tone)} style={{ flexGrow: segment.count }} />
        ))}
      </div>
      {(row.completed > 0 || row.inProgress > 0 || row.remaining > 0 || row.noShows > 0) && (
        <p className={`${styles.figures} tabular`}>
          {row.completed > 0 && <span>{t('owner.doctorCompleted', { count: row.completed })}</span>}
          {row.inProgress > 0 && <span>{t('owner.doctorInProgress', { count: row.inProgress })}</span>}
          {row.remaining > 0 && <span>{t('owner.doctorRemaining', { count: row.remaining })}</span>}
          {row.noShows > 0 && <span>{t('owner.doctorNoShows', { count: row.noShows })}</span>}
        </p>
      )}
    </li>
  )
}
