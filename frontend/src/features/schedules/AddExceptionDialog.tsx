import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextInput } from '../../shared/ui/Input'
import { FormRow } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { useToast } from '../../shared/ui/useToast'
import { useCreateScheduleException } from './api'
import { emptyException, exceptionSchema, toCreateExceptionRequest, type ExceptionValues } from './schemas'
import styles from './SchedulesPage.module.css'

interface AddExceptionDialogProps {
  doctorId: string
  /** Today in the clinic's time zone: the earliest date allowed. */
  today: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AddExceptionDialog({ doctorId, today, open, onOpenChange }: AddExceptionDialogProps) {
  const { t } = useTranslation('schedules')
  const { t: tCommon } = useTranslation()
  const formId = useId()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const create = useCreateScheduleException(doctorId)
  const form = useForm<ExceptionValues>({
    resolver: standardSchemaResolver(exceptionSchema),
    defaultValues: emptyException(today),
    mode: 'onTouched',
  })
  const { control, register, reset, setError, setFocus, handleSubmit, formState } = form
  const type = useWatch({ control, name: 'type' })

  useEffect(() => {
    if (open) {
      reset(emptyException(today))
      create.reset()
    }
    // Reset only when the dialog opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit((values) =>
    create.mutate(toCreateExceptionRequest(values), {
      onSuccess: () => {
        toast(t('exceptions.added'))
        onOpenChange(false)
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus),
    }),
  )

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !create.isPending && onOpenChange(next)}
      title={t('exceptions.addTitle')}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={create.isPending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={create.isPending}>
            {t('exceptions.add')}
          </Button>
        </>
      }
    >
      <form id={formId} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
        {create.error && !hasFieldErrors(create.error) && <Alert tone="danger">{errorMessage(create.error)}</Alert>}
        <FormRow>
          <Field label={t('exceptions.fields.date')} error={fieldError(formState.errors.date?.message)}>
            {(controlProps) => <DateInput {...controlProps} min={today} {...register('date')} />}
          </Field>
          <Field label={t('exceptions.fields.type')}>
            {(controlProps) => (
              <Controller
                control={control}
                name="type"
                render={({ field }) => (
                  <Select
                    {...controlProps}
                    name={field.name}
                    value={field.value}
                    onValueChange={field.onChange}
                    options={[
                      { value: 'UNAVAILABLE', label: t('exceptions.type.UNAVAILABLE') },
                      { value: 'EXTRA_AVAILABILITY', label: t('exceptions.type.EXTRA_AVAILABILITY') },
                    ]}
                  />
                )}
              />
            )}
          </Field>
        </FormRow>
        <FormRow>
          <Field
            label={t('exceptions.fields.startTime')}
            optional={type === 'UNAVAILABLE'}
            error={fieldError(formState.errors.startTime?.message)}
          >
            {(controlProps) => <TextInput type="time" step={60} {...controlProps} {...register('startTime')} />}
          </Field>
          <Field
            label={t('exceptions.fields.endTime')}
            optional={type === 'UNAVAILABLE'}
            error={fieldError(formState.errors.endTime?.message)}
          >
            {(controlProps) => <TextInput type="time" step={60} {...controlProps} {...register('endTime')} />}
          </Field>
        </FormRow>
        <p className={styles.note}>
          {type === 'UNAVAILABLE' ? t('exceptions.help.UNAVAILABLE') : t('exceptions.help.EXTRA_AVAILABILITY')}
        </p>
        <Field
          label={t('exceptions.fields.reason')}
          optional
          helper={t('exceptions.help.reason')}
          error={fieldError(formState.errors.reason?.message)}
        >
          {(controlProps) => <TextInput {...controlProps} {...register('reason')} />}
        </Field>
      </form>
    </Dialog>
  )
}
