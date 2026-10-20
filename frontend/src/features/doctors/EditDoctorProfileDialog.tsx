import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod/mini'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { emptyToNull, optionalText } from '../../shared/forms/fields'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { Field } from '../../shared/ui/Field'
import { TextInput } from '../../shared/ui/Input'
import { Select } from '../../shared/ui/Select'
import { useToast } from '../../shared/ui/useToast'
import { doctorName, useUpdateDoctorProfile, type Doctor } from './api'
import { useSpecialtyOptions } from './specialties'
import styles from './EditDoctorProfileDialog.module.css'

const profileSchema = z.object({
  primarySpecialty: z.string().check(z.minLength(1, { error: 'REQUIRED' })),
  additionalSpecialty: optionalText(200),
  registrationNumber: optionalText(50),
})

type ProfileValues = z.infer<typeof profileSchema>

function valuesOf(doctor: Doctor): ProfileValues {
  return {
    primarySpecialty: doctor.primarySpecialty,
    additionalSpecialty: doctor.additionalSpecialty ?? '',
    registrationNumber: doctor.registrationNumber ?? '',
  }
}

interface EditDoctorProfileDialogProps {
  doctor: Doctor
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Professional details; owners edit any doctor, doctors their own (api.md §8). Requires the `doctors` and `specialties` namespaces. */
export function EditDoctorProfileDialog({ doctor, open, onOpenChange }: EditDoctorProfileDialogProps) {
  const { t } = useTranslation('doctors')
  const { t: tCommon } = useTranslation()
  const formId = useId()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const specialtyOptions = useSpecialtyOptions()
  const update = useUpdateDoctorProfile(doctor.id)
  const form = useForm<ProfileValues>({
    resolver: standardSchemaResolver(profileSchema),
    defaultValues: valuesOf(doctor),
    mode: 'onTouched',
  })
  const { control, register, reset, setError, setFocus, handleSubmit, formState } = form

  useEffect(() => {
    if (open) {
      reset(valuesOf(doctor))
      update.reset()
    }
    // Reset only when the dialog opens, not when the doctor refreshes underneath an open form.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit((values) =>
    update.mutate(
      {
        primarySpecialty: values.primarySpecialty,
        additionalSpecialty: emptyToNull(values.additionalSpecialty),
        registrationNumber: emptyToNull(values.registrationNumber),
        version: doctor.version,
      },
      {
        onSuccess: () => {
          toast(t('profile.saved'))
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
      title={t('profile.editTitle', { name: doctorName(doctor) })}
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
      <form id={formId} className={styles.form} onSubmit={onSubmit} noValidate>
        {update.error && !hasFieldErrors(update.error) && <Alert tone="danger">{errorMessage(update.error)}</Alert>}
        <Field label={t('fields.primarySpecialty')} error={fieldError(formState.errors.primarySpecialty?.message)}>
          {(controlProps) => (
            <Controller
              control={control}
              name="primarySpecialty"
              render={({ field }) => (
                <Select
                  {...controlProps}
                  name={field.name}
                  value={field.value || undefined}
                  onValueChange={field.onChange}
                  options={specialtyOptions}
                />
              )}
            />
          )}
        </Field>
        <Field
          label={t('fields.additionalSpecialty')}
          optional
          helper={t('help.additionalSpecialty')}
          error={fieldError(formState.errors.additionalSpecialty?.message)}
        >
          {(controlProps) => <TextInput {...controlProps} {...register('additionalSpecialty')} />}
        </Field>
        <Field
          label={t('fields.registrationNumber')}
          optional
          helper={t('help.registrationNumber')}
          error={fieldError(formState.errors.registrationNumber?.message)}
        >
          {(controlProps) => <TextInput {...controlProps} {...register('registrationNumber')} />}
        </Field>
      </form>
    </Dialog>
  )
}
