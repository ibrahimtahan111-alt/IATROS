import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { emptyToNull } from '../../shared/forms/fields'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { Field } from '../../shared/ui/Field'
import { TextInput } from '../../shared/ui/Input'
import { useToast } from '../../shared/ui/useToast'
import { personName, useCancelAppointment, type Appointment } from './api'
import { cancelSchema, type CancelValues } from './schemas'
import styles from './Calendar.module.css'

interface CancelAppointmentDialogProps {
  appointment: Appointment
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Cancellation keeps the appointment in the history (ui-ux.md §10); the reason is optional. */
export function CancelAppointmentDialog({ appointment, open, onOpenChange }: CancelAppointmentDialogProps) {
  const { t } = useTranslation('calendar')
  const formId = useId()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const cancel = useCancelAppointment(appointment.id)
  const { register, reset, setError, setFocus, handleSubmit, formState } = useForm<CancelValues>({
    resolver: standardSchemaResolver(cancelSchema),
    defaultValues: { reason: '' },
  })

  useEffect(() => {
    if (open) {
      reset({ reason: '' })
      cancel.reset()
    }
    // Reset only when the dialog opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit((values) =>
    cancel.mutate(
      { reason: emptyToNull(values.reason), version: appointment.version },
      {
        onSuccess: () => {
          toast(t('cancel.done'))
          onOpenChange(false)
        },
        onError: (error) => applyFieldErrors(error, setError, setFocus),
      },
    ),
  )

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !cancel.isPending && onOpenChange(next)}
      title={t('cancel.title')}
      description={t('cancel.consequence', { name: personName(appointment.patient) })}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={cancel.isPending}>
            {t('cancel.keep')}
          </Button>
          <Button type="submit" form={formId} variant="danger" loading={cancel.isPending}>
            {t('cancel.action')}
          </Button>
        </>
      }
    >
      <form id={formId} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
        {cancel.error && !hasFieldErrors(cancel.error) && <Alert tone="danger">{errorMessage(cancel.error)}</Alert>}
        <Field label={t('fields.cancellationReason')} optional error={fieldError(formState.errors.reason?.message)}>
          {(controlProps) => <TextInput {...controlProps} {...register('reason')} />}
        </Field>
      </form>
    </Dialog>
  )
}
