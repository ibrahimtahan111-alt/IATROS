import { CalendarClock, UserPlus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { todayIn } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page, Section } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useSessionUser } from '../auth/session'
import { doctorName, useDoctorChoices, type Doctor } from '../doctors/api'
import { useWeeklySchedule } from './api'
import { ExceptionsSection } from './ExceptionsSection'
import styles from './SchedulesPage.module.css'
import { WeeklyHoursEditor } from './WeeklyHoursEditor'
import { WorkingHoursPreview } from './WorkingHoursPreview'

/**
 * Doctor schedules (roadmap.md Phase 6). Owners pick any clinic doctor (`?doctor=`); doctors manage their own
 * schedule. Secretaries read availability through booking, not here (roles-and-permissions.md).
 */
export function SchedulesPage() {
  const user = useSessionUser()
  const { t } = useTranslation()

  if (user.role !== 'OWNER') {
    return (
      <Page>
        <ContextBar title={t('nav.mySchedule')} />
        {user.doctorId ? <DoctorSchedule key={user.doctorId} doctorId={user.doctorId} /> : <NoDoctors />}
      </Page>
    )
  }
  return <OwnerSchedules />
}

function OwnerSchedules() {
  const { t } = useTranslation('schedules')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const doctors = useDoctorChoices()
  const [searchParams, setSearchParams] = useSearchParams()
  const title = tCommon('nav.schedules')

  if (doctors.isPending) {
    return (
      <Page>
        <ContextBar title={title} />
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Page>
    )
  }
  if (doctors.isError) {
    return (
      <Page>
        <ContextBar title={title} />
        <ErrorState error={doctors.error} onRetry={() => void doctors.refetch()} retrying={doctors.isFetching} />
      </Page>
    )
  }
  if (doctors.data.length === 0) {
    return (
      <Page>
        <ContextBar title={title} />
        <NoDoctors canAdd />
      </Page>
    )
  }

  const requested = doctors.data.find((doctor) => doctor.id === searchParams.get('doctor'))
  const selected: Doctor =
    requested ?? doctors.data.find((doctor) => doctor.id === user.doctorId) ?? doctors.data[0]!
  const label = (doctor: Doctor) =>
    doctor.status === 'DEACTIVATED' ? t('picker.deactivated', { name: doctorName(doctor) }) : doctorName(doctor)

  return (
    <Page>
      <ContextBar
        title={title}
        scope={
          <div className={styles.picker}>
            <Select
              aria-label={t('picker.label')}
              value={selected.id}
              onValueChange={(id) =>
                setSearchParams(
                  (current) => {
                    const next = new URLSearchParams(current)
                    next.set('doctor', id)
                    return next
                  },
                  { replace: true },
                )
              }
              options={doctors.data.map((doctor) => ({ value: doctor.id, label: label(doctor) }))}
            />
          </div>
        }
      />
      {selected.status === 'DEACTIVATED' && (
        <Alert tone="warning" className={styles.banner}>
          {t('deactivated', { name: doctorName(selected) })}
        </Alert>
      )}
      <DoctorSchedule key={selected.id} doctorId={selected.id} />
    </Page>
  )
}

function DoctorSchedule({ doctorId }: { doctorId: string }) {
  const { t } = useTranslation('schedules')
  const { t: tCommon } = useTranslation()
  const schedule = useWeeklySchedule(doctorId)

  if (schedule.isPending) {
    return (
      <LoadingRegion label={tCommon('states.loading')}>
        <Skeleton height="var(--space-16)" />
      </LoadingRegion>
    )
  }
  if (schedule.isError) {
    return <ErrorState error={schedule.error} onRetry={() => void schedule.refetch()} retrying={schedule.isFetching} />
  }

  const { timeZone } = schedule.data
  const today = todayIn(timeZone)
  return (
    <>
      <Section title={t('weekly.title')} description={t('weekly.description', { timeZone })}>
        <WeeklyHoursEditor schedule={schedule.data} />
      </Section>
      <ExceptionsSection doctorId={doctorId} timeZone={timeZone} today={today} />
      <WorkingHoursPreview doctorId={doctorId} timeZone={timeZone} today={today} />
    </>
  )
}

function NoDoctors({ canAdd = false }: { canAdd?: boolean }) {
  const { t } = useTranslation('schedules')
  return (
    <EmptyState
      icon={<CalendarClock />}
      title={t('empty.title')}
      description={t(canAdd ? 'empty.description' : 'empty.notADoctor')}
      action={
        canAdd ? (
          <ButtonLink to="/team/new" variant="primary" icon={<UserPlus aria-hidden="true" />}>
            {t('empty.addDoctor')}
          </ButtonLink>
        ) : undefined
      }
    />
  )
}
