import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { Field } from '../../shared/ui/Field'
import { TextInput } from '../../shared/ui/Input'
import { FormActions, FormRow, Page, Section } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useToast } from '../../shared/ui/useToast'
import { useSpecialtyOptions } from '../doctors/specialties'
import { fullName, useCreateStaffMember, type StaffMember } from './api'
import { ContactFields } from './ContactFields'
import { RoleChoice } from './RoleChoice'
import { EMPTY_NEW_MEMBER, newMemberSchema, toCreateRequest, type NewMemberValues } from './schemas'
import styles from './NewMemberPage.module.css'

/** Full-page form: adding a member is a multi-section task (design-system.md §4.5). */
export function NewMemberPage() {
  const { t } = useTranslation('team')
  const { t: tCommon } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const create = useCreateStaffMember()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const specialtyOptions = useSpecialtyOptions()
  const [created, setCreated] = useState<StaffMember | null>(null)
  const form = useForm<NewMemberValues>({
    resolver: standardSchemaResolver(newMemberSchema),
    defaultValues: EMPTY_NEW_MEMBER,
    mode: 'onTouched',
  })
  const { register, control, handleSubmit, formState, setError, setFocus } = form
  const blocker = useUnsavedChangesGuard(formState.isDirty && created === null)
  const role = useWatch({ control, name: 'role' })

  useEffect(() => {
    if (created) {
      void navigate(`/team/${created.id}`, { replace: true })
    }
  }, [created, navigate])

  const onSubmit = handleSubmit((values) =>
    create.mutate(toCreateRequest(values), {
      onSuccess: (member) => {
        toast(t('new.created', { name: fullName(member) }))
        setCreated(member)
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus),
    }),
  )

  return (
    <Page>
      <ContextBar title={t('new.title')} />
      <form onSubmit={onSubmit} noValidate>
        {create.error && !hasFieldErrors(create.error) && (
          <Alert tone="danger" className={styles.alert}>
            {errorMessage(create.error)}
          </Alert>
        )}

        <Section title={t('new.basics')}>
          <ContactFields form={form} emailEditable />
        </Section>

        <Section title={t('new.role')}>
          <RoleChoice register={register} error={fieldError(formState.errors.role?.message)} />
        </Section>

        {role === 'DOCTOR' && (
          <Section title={t('new.doctor')} description={t('new.scheduleLater')}>
            <FormRow>
              <Field label={t('fields.primarySpecialty')} error={fieldError(formState.errors.doctor?.primarySpecialty?.message)}>
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="doctor.primarySpecialty"
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
                label={t('fields.registrationNumber')}
                optional
                helper={t('help.registrationNumber')}
                error={fieldError(formState.errors.doctor?.registrationNumber?.message)}
              >
                {(controlProps) => <TextInput {...controlProps} {...register('doctor.registrationNumber')} />}
              </Field>
            </FormRow>
            <Field
              label={t('fields.additionalSpecialty')}
              optional
              helper={t('help.additionalSpecialty')}
              error={fieldError(formState.errors.doctor?.additionalSpecialty?.message)}
            >
              {(controlProps) => <TextInput {...controlProps} {...register('doctor.additionalSpecialty')} />}
            </Field>
          </Section>
        )}

        <FormActions>
          <Button type="submit" variant="primary" loading={create.isPending}>
            {t('new.submit')}
          </Button>
          <ButtonLink to="/team" variant="ghost">
            {tCommon('actions.cancel')}
          </ButtonLink>
        </FormActions>
      </form>
      <UnsavedChangesDialog blocker={blocker} />
    </Page>
  )
}
