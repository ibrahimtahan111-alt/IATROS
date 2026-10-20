import { CalendarPlus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { localDateTimeOf, todayIn } from '../../shared/lib/localDates'
import { Button } from '../../shared/ui/Button'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Section } from '../../shared/ui/Page'
import { Pagination } from '../../shared/ui/Pagination'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { StatusIndicator } from '../../shared/ui/StatusIndicator'
import { useSessionUser } from '../auth/session'
import { useClinic } from '../clinic/api'
import { PATIENT_APPOINTMENTS_PAGE_SIZE, personName, usePatientAppointments, type NameRef } from './api'
import { BookAppointmentDialog, type BookingDraft } from './BookAppointmentDialog'
import styles from './Calendar.module.css'

interface PatientAppointmentsSectionProps {
  patient: NameRef
  /** Archived patients cannot be booked (D-038). */
  canBook: boolean
}

/** A patient's appointments on their profile, latest first; each opens in the calendar. */
export function PatientAppointmentsSection({ patient, canBook }: PatientAppointmentsSectionProps) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const clinic = useClinic()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const [page, setPage] = useState(0)
  const [booking, setBooking] = useState<BookingDraft | null>(null)
  const appointments = usePatientAppointments(patient.id, page)
  const data = appointments.data

  return (
    <Section
      title={t('patientSection.title')}
      description={t('patientSection.description')}
      action={
        canBook && (
          <Button
            size="sm"
            icon={<CalendarPlus aria-hidden="true" />}
            onClick={() => setBooking({ date: todayIn(user.clinic.timeZone), patient })}
          >
            {t('patientSection.book')}
          </Button>
        )
      }
    >
      {appointments.isError ? (
        <ErrorState error={appointments.error} onRetry={() => void appointments.refetch()} retrying={appointments.isFetching} />
      ) : !data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-10)" />
        </LoadingRegion>
      ) : data.totalElements === 0 ? (
        <p className={styles.muted}>{t('patientSection.empty')}</p>
      ) : (
        <>
          <ul className={styles.list} aria-busy={appointments.isFetching || undefined}>
            {data.content.map((appointment) => {
              const date = localDateTimeOf(appointment.startAt, user.clinic.timeZone).slice(0, 10)
              return (
                <li key={appointment.id} className={styles.listItem}>
                  <Link
                    className={styles.listButton}
                    to={`/calendar?view=day&date=${date}&appointment=${appointment.id}`}
                  >
                    <span className={`${styles.listTitle} tabular`}>
                      {format.date(appointment.startAt)} · {format.timeRangeOf(appointment.startAt, appointment.endAt)}
                    </span>
                    <span className={styles.muted}>
                      {personName(appointment.doctor)} · {t(`type.${appointment.type}`)}
                    </span>
                  </Link>
                  <StatusIndicator status={appointment.status} />
                </li>
              )
            })}
          </ul>
          {data.totalElements > PATIENT_APPOINTMENTS_PAGE_SIZE && (
            <Pagination page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
          )}
        </>
      )}
      <BookAppointmentDialog
        draft={booking}
        onOpenChange={(open) => !open && setBooking(null)}
        defaultDuration={clinic.data?.settings.defaultAppointmentMinutes ?? 30}
      />
    </Section>
  )
}
