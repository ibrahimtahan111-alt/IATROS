import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { Field } from '../../shared/ui/Field'
import { TextArea, TextInput } from '../../shared/ui/Input'
import { useToast } from '../../shared/ui/useToast'
import { useUpdateAppointment, type Appointment } from './api'
import { AppointmentTypeSelect } from './AppointmentTypeSelect'
import { detailsSchema, detailsValuesOf, toDetailsRequest, type DetailsValues } from './schemas'
import styles from './Calendar.module.css'

interface EditAppointmentDialogProps {
  appointment: Appointment
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Type, reason, and notes. The time changes through rescheduling, which checks availability. */
export function EditAppointmentDialog({ appointment, open, onOpenChange }: EditAppointmentDialogProps) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const formId = useId()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const update = useUpdateAppointment(appointment.id)
  const form = useForm<DetailsValues>({
    resolver: standardSchemaResolver(detailsSchema),
    defaultValues: detailsValuesOf(appointment),
    mode: 'onTouched',
  })
  const { control, register, reset, setError, setFocus, handleSubmit, formState } = form

  useEffect(() => {
    if (open) {
      reset(detailsValuesOf(appointment))
      update.reset()
    }
    // Reset only when the dialog opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit((values) =>
    update.mutate(
      { ...toDetailsRequest(values), version: appointment.version },
      {
        onSuccess: () => {
          toast(t('edit.done'))
          onOpenChange(false)
        },
        onError: (error) => applyFieldErrors(error, setError, setFocus),
      },
    ),
  )

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !update.isPending && onOpenChange(next)}
      title={t('edit.title')}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={update.isPending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={update.isPending}>
            {tCommon('actions.save')}
          </Button>
        </>
      }
    >
      <form id={formId} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
        {update.error && !hasFieldErrors(update.error) && <Alert tone="danger">{errorMessage(update.error)}</Alert>}
        <Field label={t('fields.type')}>
          {(controlProps) => (
            <Controller
              control={control}
              name="type"
              render={({ field }) => (
                <AppointmentTypeSelect {...controlProps} value={field.value} onValueChange={field.onChange} />
              )}
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
    </Dialog>
  )
}
