import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { localDateTimeOf } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { useRescheduleAppointment, type Appointment } from './api'
import { AppointmentTimeFields } from './AppointmentTimeFields'
import { rescheduleSchema, SERVER_FIELDS, timesOf, type RescheduleValues } from './schemas'
import styles from './Calendar.module.css'

interface AppointmentDialogProps {
  appointment: Appointment
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Moves an appointment to another time or doctor; the server checks availability and conflicts again. */
export function RescheduleDialog({ appointment, open, onOpenChange }: AppointmentDialogProps) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const formId = useId()
  const reschedule = useRescheduleAppointment(appointment.id)

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !reschedule.isPending && onOpenChange(next)}
      title={t('reschedule.title')}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={reschedule.isPending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={reschedule.isPending}>
            {t('reschedule.submit')}
          </Button>
        </>
      }
    >
      {open && (
        <RescheduleForm id={formId} appointment={appointment} reschedule={reschedule} onDone={() => onOpenChange(false)} />
      )}
    </Dialog>
  )
}

interface RescheduleFormProps {
  id: string
  appointment: Appointment
  reschedule: ReturnType<typeof useRescheduleAppointment>
  onDone: () => void
}

function RescheduleForm({ id, appointment, reschedule, onDone }: RescheduleFormProps) {
  const { t } = useTranslation('calendar')
  const user = useSessionUser()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const start = localDateTimeOf(appointment.startAt, user.clinic.timeZone)
  const form = useForm<RescheduleValues>({
    resolver: standardSchemaResolver(rescheduleSchema),
    defaultValues: {
      doctorId: appointment.doctor.id,
      date: start.slice(0, 10),
      time: start.slice(11),
      duration: String((Date.parse(appointment.endAt) - Date.parse(appointment.startAt)) / 60_000),
    },
    mode: 'onTouched',
  })
  const { setError, setFocus, handleSubmit } = form

  useEffect(() => {
    reschedule.reset()
    // Clear the previous attempt's error only when the form mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onSubmit = handleSubmit((values) =>
    reschedule.mutate(
      { ...timesOf(values, user.clinic.timeZone), version: appointment.version },
      {
        onSuccess: () => {
          toast(t('reschedule.done'))
          onDone()
        },
        onError: (error) => applyFieldErrors(error, setError, setFocus, SERVER_FIELDS),
      },
    ),
  )

  return (
    <form id={id} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
      {reschedule.error && !hasFieldErrors(reschedule.error) && (
        <Alert tone="danger">{errorMessage(reschedule.error)}</Alert>
      )}
      <AppointmentTimeFields form={form} doctorFixed={user.role === 'DOCTOR'} />
    </form>
  )
}
