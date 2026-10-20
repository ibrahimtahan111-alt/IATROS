import { CalendarClock, ClipboardList, FilePlus2, Pencil, Stethoscope, UserRound, XCircle } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import type { AppointmentStatus } from '../../shared/domain/appointmentStatus'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextPanel } from '../../shared/ui/ContextPanel'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { StatusIndicator } from '../../shared/ui/StatusIndicator'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { personName, useAppointment, useChangeAppointmentStatus, type Appointment } from './api'
import { CancelAppointmentDialog } from './CancelAppointmentDialog'
import { EditAppointmentDialog } from './EditAppointmentDialog'
import { RescheduleDialog } from './RescheduleDialog'
import styles from './Calendar.module.css'

/** Statuses that may start the visit's consultation (D-038). */
const CONSULTATION_STATUSES: readonly AppointmentStatus[] = ['WAITING', 'IN_CONSULTATION', 'COMPLETED']

interface AppointmentPanelProps {
  appointmentId: string | null
  onClose: () => void
}

/** Appointment details and quick actions beside the calendar (ui-ux.md §10). */
export function AppointmentPanel({ appointmentId, onClose }: AppointmentPanelProps) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const appointment = useAppointment(appointmentId)
  const data = appointment.data

  return (
    <ContextPanel
      open={appointmentId !== null}
      onOpenChange={(open) => !open && onClose()}
      title={data ? personName(data.patient) : t('panel.title')}
      subtitle={data && <StatusIndicator status={data.status} />}
    >
      {appointment.isError ? (
        <ErrorState error={appointment.error} onRetry={() => void appointment.refetch()} retrying={appointment.isFetching} />
      ) : !data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      ) : (
        <AppointmentDetails appointment={data} />
      )}
    </ContextPanel>
  )
}

function AppointmentDetails({ appointment }: { appointment: Appointment }) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const changeStatus = useChangeAppointmentStatus(appointment.id)
  const [dialog, setDialog] = useState<'edit' | 'reschedule' | 'cancel' | null>(null)
  const clinical = user.role !== 'SECRETARY'
  const patientPath = `/patients/${appointment.patient.id}`
  const canStartConsultation =
    user.role !== 'SECRETARY' &&
    user.doctorId === appointment.doctor.id &&
    appointment.consultationId === null &&
    CONSULTATION_STATUSES.includes(appointment.status)
  const notSet = <span className={styles.muted}>—</span>

  const setStatus = (status: AppointmentStatus) =>
    changeStatus.mutate(
      { status, version: appointment.version },
      { onSuccess: () => toast(t('status.done', { status: tCommon(`appointmentStatus.${status}`) })) },
    )

  return (
    <>
      {changeStatus.error && <Alert tone="danger">{errorMessage(changeStatus.error)}</Alert>}
      <dl className={styles.details}>
        <dt>{t('fields.when')}</dt>
        <dd className="tabular">
          {format.date(appointment.startAt)} · {format.timeRangeOf(appointment.startAt, appointment.endAt)}
        </dd>
        <dt>{t('fields.doctor')}</dt>
        <dd>{personName(appointment.doctor)}</dd>
        <dt>{t('fields.type')}</dt>
        <dd>{t(`type.${appointment.type}`)}</dd>
        <dt>{t('fields.reason')}</dt>
        <dd>{appointment.reason ?? notSet}</dd>
        <dt>{t('fields.notes')}</dt>
        <dd>{appointment.notes ?? notSet}</dd>
        {appointment.status === 'CANCELLED' && (
          <>
            <dt>{t('fields.cancellationReason')}</dt>
            <dd>{appointment.cancellationReason ?? notSet}</dd>
          </>
        )}
        <dt>{t('fields.bookedBy')}</dt>
        <dd>
          {appointment.createdBy ? personName(appointment.createdBy) : notSet}
          <span className={styles.muted}> · {format.dateTime(appointment.createdAt)}</span>
        </dd>
      </dl>

      {appointment.nextStatuses.length > 0 && (
        <div className={styles.links} role="group" aria-label={t('status.label')}>
          {appointment.nextStatuses.map((status) => (
            <Button
              key={status}
              size="sm"
              variant={status === 'NO_SHOW' ? 'ghost' : 'secondary'}
              loading={changeStatus.isPending && changeStatus.variables?.status === status}
              disabled={changeStatus.isPending}
              onClick={() => setStatus(status)}
            >
              {t(`status.action.${status}`)}
            </Button>
          ))}
        </div>
      )}

      <div className={styles.links}>
        {canStartConsultation && (
          <ButtonLink
            size="sm"
            variant="primary"
            icon={<FilePlus2 aria-hidden="true" />}
            to={`${patientPath}/consultations/new?appointment=${appointment.id}`}
          >
            {t('panel.startConsultation')}
          </ButtonLink>
        )}
        {clinical && appointment.consultationId && (
          <ButtonLink
            size="sm"
            icon={<Stethoscope aria-hidden="true" />}
            to={`${patientPath}/consultations/${appointment.consultationId}`}
          >
            {t('panel.viewConsultation')}
          </ButtonLink>
        )}
        <ButtonLink size="sm" variant="ghost" icon={<UserRound aria-hidden="true" />} to={patientPath}>
          {t('panel.openPatient')}
        </ButtonLink>
        {clinical && (
          <ButtonLink size="sm" variant="ghost" icon={<ClipboardList aria-hidden="true" />} to={`${patientPath}/medical-record`}>
            {t('panel.medicalRecord')}
          </ButtonLink>
        )}
      </div>

      {(appointment.editable || appointment.cancellable) && (
        <div className={styles.links}>
          {appointment.editable && (
            <>
              <Button size="sm" icon={<CalendarClock aria-hidden="true" />} onClick={() => setDialog('reschedule')}>
                {t('reschedule.action')}
              </Button>
              <Button size="sm" icon={<Pencil aria-hidden="true" />} onClick={() => setDialog('edit')}>
                {t('edit.action')}
              </Button>
            </>
          )}
          {appointment.cancellable && (
            <Button size="sm" variant="danger" icon={<XCircle aria-hidden="true" />} onClick={() => setDialog('cancel')}>
              {t('cancel.action')}
            </Button>
          )}
        </div>
      )}

      <RescheduleDialog appointment={appointment} open={dialog === 'reschedule'} onOpenChange={(open) => !open && setDialog(null)} />
      <EditAppointmentDialog appointment={appointment} open={dialog === 'edit'} onOpenChange={(open) => !open && setDialog(null)} />
      <CancelAppointmentDialog appointment={appointment} open={dialog === 'cancel'} onOpenChange={(open) => !open && setDialog(null)} />
    </>
  )
}
