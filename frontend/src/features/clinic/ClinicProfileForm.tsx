import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { TextInput } from '../../shared/ui/Input'
import { FormActions, FormRow, FormStack } from '../../shared/ui/Page'
import { useToast } from '../../shared/ui/useToast'
import { useUpdateClinic, type Clinic } from './api'
import { clinicProfileSchema, profileValues, toUpdateClinicRequest, type ClinicProfileValues } from './schemas'

interface ClinicProfileFormProps {
  clinic: Clinic
  onDirtyChange: (dirty: boolean) => void
}

export function ClinicProfileForm({ clinic, onDirtyChange }: ClinicProfileFormProps) {
  const { t } = useTranslation('clinic')
  const { t: tCommon } = useTranslation()
  const toast = useToast()
  const update = useUpdateClinic()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const { register, handleSubmit, formState, reset, setError, setFocus } = useForm<ClinicProfileValues>({
    resolver: standardSchemaResolver(clinicProfileSchema),
    defaultValues: profileValues(clinic),
    mode: 'onTouched',
  })
  const { errors, isDirty } = formState

  useEffect(() => onDirtyChange(isDirty), [isDirty, onDirtyChange])

  const onSubmit = handleSubmit((values) =>
    update.mutate(toUpdateClinicRequest(values, clinic.version), {
      onSuccess: (saved) => {
        reset(profileValues(saved))
        toast(t('profile.saved'))
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus),
    }),
  )

  return (
    <FormStack onSubmit={onSubmit}>
      {update.error && !hasFieldErrors(update.error) && <Alert tone="danger">{errorMessage(update.error)}</Alert>}
      <Field label={t('profile.name')} error={fieldError(errors.name?.message)}>
        {(control) => <TextInput {...control} autoComplete="organization" {...register('name')} />}
      </Field>
      <FormRow>
        <Field label={t('profile.email')} optional error={fieldError(errors.email?.message)}>
          {(control) => <TextInput {...control} type="email" autoComplete="email" {...register('email')} />}
        </Field>
        <Field label={t('profile.phone')} optional error={fieldError(errors.phone?.message)}>
          {(control) => <TextInput {...control} type="tel" autoComplete="tel" {...register('phone')} />}
        </Field>
      </FormRow>
      <Field label={t('profile.address')} optional error={fieldError(errors.address?.message)}>
        {(control) => <TextInput {...control} autoComplete="street-address" {...register('address')} />}
      </Field>
      <FormActions>
        <Button type="submit" variant="primary" loading={update.isPending} disabled={!isDirty}>
          {tCommon('actions.save')}
        </Button>
      </FormActions>
    </FormStack>
  )
}
