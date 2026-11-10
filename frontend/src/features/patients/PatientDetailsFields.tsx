import { Controller, type Control, type FieldErrors, type Path, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextInput } from '../../shared/ui/Input'
import { FormRow, Section } from '../../shared/ui/Page'
import { PhoneInput } from '../../shared/ui/PhoneInput'
import { Select } from '../../shared/ui/Select'
import { useDefaultPhoneRegion } from '../clinic/phoneRegion'
import { GENDERS } from './api'
import type { PatientDetailsValues } from './schemas'

interface PatientFormProps<T extends PatientDetailsValues> {
  form: UseFormReturn<T>
  /** Clinic-local date: the latest selectable birth date. */
  today: string
}

/** Personal and contact sections of the edit page (ui-ux.md §13). */
export function PatientDetailsFields<T extends PatientDetailsValues>({ form, today }: PatientFormProps<T>) {
  const { t } = useTranslation('patients')
  const field = (name: keyof PatientDetailsValues) => <PatientDetailField form={form} today={today} name={name} />

  return (
    <>
      <Section title={t('form.personal')}>
        <FormRow>
          {field('firstName')}
          {field('lastName')}
        </FormRow>
        <FormRow>
          {field('dateOfBirth')}
          {field('gender')}
        </FormRow>
      </Section>

      <Section title={t('form.contact')}>
        <FormRow>
          {field('phone')}
          {field('email')}
        </FormRow>
        {field('address')}
        <FormRow>
          {field('emergencyContactName')}
          {field('emergencyContactPhone')}
        </FormRow>
      </Section>
    </>
  )
}

/** One labelled patient detail, so the add dialog and the edit page can lay the same fields out differently. */
export function PatientDetailField<T extends PatientDetailsValues>({
  form,
  today,
  name,
}: PatientFormProps<T> & { name: keyof PatientDetailsValues }) {
  const { t } = useTranslation('patients')
  const fieldError = useFieldErrorMessage()
  const errors = form.formState.errors as FieldErrors<PatientDetailsValues>
  const register = (field: keyof PatientDetailsValues) => form.register(field as Path<T>)
  const control = form.control as unknown as Control<PatientDetailsValues>
  const label = t(`fields.${name}`)
  const error = fieldError(errors[name]?.message)

  switch (name) {
    case 'firstName':
    case 'lastName':
      return (
        <Field label={label} error={error}>
          {(props) => <TextInput {...props} autoComplete="off" {...register(name)} />}
        </Field>
      )
    case 'dateOfBirth':
      return (
        <Field label={label} error={error}>
          {(props) => <DateInput {...props} min="1900-01-01" max={today} {...register(name)} />}
        </Field>
      )
    case 'gender':
      return (
        <Field label={label} optional>
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
      )
    case 'phone':
    case 'emergencyContactPhone':
      return <PatientPhoneField control={control} name={name} label={label} error={error} />
    case 'email':
      return (
        <Field label={label} optional error={error}>
          {(props) => <TextInput {...props} type="email" autoComplete="off" {...register(name)} />}
        </Field>
      )
    case 'address':
    case 'emergencyContactName':
      return (
        <Field label={label} optional error={error}>
          {(props) => <TextInput {...props} autoComplete="off" {...register(name)} />}
        </Field>
      )
  }
}

interface PatientPhoneFieldProps {
  control: Control<PatientDetailsValues>
  name: 'phone' | 'emergencyContactPhone'
  label: string
  error: string | undefined
}

function PatientPhoneField({ control, name, label, error }: PatientPhoneFieldProps) {
  const defaultRegion = useDefaultPhoneRegion()
  return (
    <Field label={label} optional error={error}>
      {(props) => (
        <Controller
          control={control}
          name={name}
          render={({ field }) => (
            <PhoneInput
              {...props}
              ref={field.ref}
              name={field.name}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              defaultRegion={defaultRegion}
            />
          )}
        />
      )}
    </Field>
  )
}
