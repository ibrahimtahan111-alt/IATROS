import { Controller, type Control, type FieldErrors, type Path, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextInput } from '../../shared/ui/Input'
import { FormRow, Section } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { GENDERS } from './api'
import type { PatientDetailsValues } from './schemas'

interface PatientDetailsFieldsProps<T extends PatientDetailsValues> {
  form: UseFormReturn<T>
  /** Clinic-local date: the latest selectable birth date. */
  today: string
}

/** Personal and contact sections, shared by the add and edit pages (ui-ux.md §13). */
export function PatientDetailsFields<T extends PatientDetailsValues>({ form, today }: PatientDetailsFieldsProps<T>) {
  const { t } = useTranslation('patients')
  const fieldError = useFieldErrorMessage()
  const errors = form.formState.errors as FieldErrors<PatientDetailsValues>
  const register = (name: keyof PatientDetailsValues) => form.register(name as Path<T>)
  const control = form.control as unknown as Control<PatientDetailsValues>

  return (
    <>
      <Section title={t('form.personal')}>
        <FormRow>
          <Field label={t('fields.firstName')} error={fieldError(errors.firstName?.message)}>
            {(props) => <TextInput {...props} autoComplete="off" {...register('firstName')} />}
          </Field>
          <Field label={t('fields.lastName')} error={fieldError(errors.lastName?.message)}>
            {(props) => <TextInput {...props} autoComplete="off" {...register('lastName')} />}
          </Field>
        </FormRow>
        <FormRow>
          <Field label={t('fields.dateOfBirth')} error={fieldError(errors.dateOfBirth?.message)}>
            {(props) => <DateInput {...props} min="1900-01-01" max={today} {...register('dateOfBirth')} />}
          </Field>
          <Field label={t('fields.gender')} optional>
            {(props) => (
              <Controller
                control={control}
                name="gender"
                render={({ field }) => (
                  <Select
                    {...props}
                    name={field.name}
                    value={field.value || undefined}
                    onValueChange={field.onChange}
                    placeholder={t('form.genderPlaceholder')}
                    options={GENDERS.map((gender) => ({ value: gender, label: t(`gender.${gender}`) }))}
                  />
                )}
              />
            )}
          </Field>
        </FormRow>
      </Section>

      <Section title={t('form.contact')}>
        <FormRow>
          <Field label={t('fields.phone')} optional error={fieldError(errors.phone?.message)}>
            {(props) => <TextInput {...props} type="tel" autoComplete="off" {...register('phone')} />}
          </Field>
          <Field label={t('fields.email')} optional error={fieldError(errors.email?.message)}>
            {(props) => <TextInput {...props} type="email" autoComplete="off" {...register('email')} />}
          </Field>
        </FormRow>
        <Field label={t('fields.address')} optional error={fieldError(errors.address?.message)}>
          {(props) => <TextInput {...props} autoComplete="off" {...register('address')} />}
        </Field>
        <FormRow>
          <Field
            label={t('fields.emergencyContactName')}
            optional
            error={fieldError(errors.emergencyContactName?.message)}
          >
            {(props) => <TextInput {...props} autoComplete="off" {...register('emergencyContactName')} />}
          </Field>
          <Field
            label={t('fields.emergencyContactPhone')}
            optional
            error={fieldError(errors.emergencyContactPhone?.message)}
          >
            {(props) => <TextInput {...props} type="tel" autoComplete="off" {...register('emergencyContactPhone')} />}
          </Field>
        </FormRow>
      </Section>
    </>
  )
}
