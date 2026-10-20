import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { Field } from '../../shared/ui/Field'
import { TextArea, TextInput } from '../../shared/ui/Input'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { useCreateAppointment, type Appointment, type NameRef } from './api'
import { AppointmentTimeFields } from './AppointmentTimeFields'
import { AppointmentTypeSelect } from './AppointmentTypeSelect'
import { PatientPicker } from './PatientPicker'
import { bookingSchema, SERVER_FIELDS, toBookingRequest, type BookingValues } from './schemas'
import styles from './Calendar.module.css'

export interface BookingDraft {
  date: string
  time?: string
  doctorId?: string
  patient?: NameRef
}

interface BookAppointmentDialogProps {
  draft: BookingDraft | null
  onOpenChange: (open: boolean) => void
  defaultDuration: number
  onBooked?: (appointment: Appointment) => void
}

/** Booking (ui-ux.md §10). The server decides availability and conflicts; its refusal keeps what was entered. */
export function BookAppointmentDialog({ draft, onOpenChange, defaultDuration, onBooked }: BookAppointmentDialogProps) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const formId = useId()
  const create = useCreateAppointment()

  return (
    <Dialog
      open={draft !== null}
      onOpenChange={(next) => !create.isPending && onOpenChange(next)}
      title={t('book.title')}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={create.isPending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={create.isPending}>
            {t('book.submit')}
          </Button>
        </>
      }
    >
      {draft && (
        <BookingForm
          id={formId}
          draft={draft}
          defaultDuration={defaultDuration}
          create={create}
          onBooked={(appointment) => {
            onOpenChange(false)
            onBooked?.(appointment)
          }}
        />
      )}
    </Dialog>
  )
}

interface BookingFormProps {
  id: string
  draft: BookingDraft
  defaultDuration: number
  create: ReturnType<typeof useCreateAppointment>
  onBooked: (appointment: Appointment) => void
}

/** Mounted with the open dialog, so every opening starts from its draft. */
function BookingForm({ id, draft, defaultDuration, create, onBooked }: BookingFormProps) {
  const { t } = useTranslation('calendar')
  const user = useSessionUser()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const [patient, setPatient] = useState<NameRef | null>(draft.patient ?? null)
  const form = useForm<BookingValues>({
    resolver: standardSchemaResolver(bookingSchema),
    defaultValues: {
      patientId: draft.patient?.id ?? '',
      doctorId: (user.role === 'DOCTOR' ? user.doctorId : draft.doctorId) ?? '',
      date: draft.date,
      time: draft.time ?? '',
      duration: String(defaultDuration),
      type: 'FOLLOW_UP',
      reason: '',
      notes: '',
    },
    mode: 'onTouched',
  })
  const { control, register, setValue, setError, setFocus, handleSubmit, formState } = form
  const patientId = useWatch({ control, name: 'patientId' })

  useEffect(() => {
    create.reset()
    // Clear the previous attempt's error only when the form mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onSubmit = handleSubmit((values) =>
    create.mutate(toBookingRequest(values, user.clinic.timeZone), {
      onSuccess: (appointment) => {
        toast(t('book.done'))
        onBooked(appointment)
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus, SERVER_FIELDS),
    }),
  )

  return (
    <form id={id} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
      {create.error && !hasFieldErrors(create.error) && <Alert tone="danger">{errorMessage(create.error)}</Alert>}
      <div>
        <p className={styles.slotsLabel}>{t('fields.patient')}</p>
        <PatientPicker
          value={patient}
          timeZone={user.clinic.timeZone}
          error={patientId === '' ? fieldError(formState.errors.patientId?.message) : undefined}
          onChange={(next) => {
            setPatient(next)
            setValue('patientId', next?.id ?? '', { shouldValidate: formState.isSubmitted })
          }}
        />
      </div>
      <AppointmentTimeFields form={form} doctorFixed={user.role === 'DOCTOR'} />
      <Field label={t('fields.type')}>
        {(controlProps) => (
          <Controller
            control={control}
            name="type"
            render={({ field }) => <AppointmentTypeSelect {...controlProps} value={field.value} onValueChange={field.onChange} />}
          />
        )}
      </Field>
      <Field label={t('fields.reason')} optional error={fieldError(formState.errors.reason?.message)}>
        {(controlProps) => <TextInput {...controlProps} {...register('reason')} />}
      </Field>
      <Field label={t('fields.notes')} optional helper={t('help.notes')} error={fieldError(formState.errors.notes?.message)}>
        {(controlProps) => <TextArea rows={3} {...controlProps} {...register('notes')} />}
      </Field>
    </form>
  )
}
