import type { FieldErrors, Path, UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { Field } from '../../shared/ui/Field'
import { TextInput } from '../../shared/ui/Input'
import { FormRow } from '../../shared/ui/Page'
import type { EditMemberValues } from './schemas'

interface ContactFieldsProps<T extends EditMemberValues> {
  form: UseFormReturn<T>
  /** The sign-in email can only change while the invitation is pending (decisions.md D-034). */
  emailEditable: boolean
}

/** Name, email, and phone: shared by the add and edit forms. */
export function ContactFields<T extends EditMemberValues>({ form, emailEditable }: ContactFieldsProps<T>) {
  const { t } = useTranslation('team')
  const fieldError = useFieldErrorMessage()
  const errors = form.formState.errors as FieldErrors<EditMemberValues>
  const register = (name: keyof EditMemberValues) => form.register(name as Path<T>)

  return (
    <>
      <FormRow>
        <Field label={t('fields.firstName')} error={fieldError(errors.firstName?.message)}>
          {(control) => <TextInput {...control} autoComplete="off" {...register('firstName')} />}
        </Field>
        <Field label={t('fields.lastName')} error={fieldError(errors.lastName?.message)}>
          {(control) => <TextInput {...control} autoComplete="off" {...register('lastName')} />}
        </Field>
      </FormRow>
      <FormRow>
        <Field
          label={t('fields.email')}
          helper={emailEditable ? t('help.email') : t('help.emailLocked')}
          error={fieldError(errors.email?.message)}
        >
          {(control) => (
            <TextInput {...control} type="email" autoComplete="off" readOnly={!emailEditable} {...register('email')} />
          )}
        </Field>
        <Field label={t('fields.phone')} optional error={fieldError(errors.phone?.message)}>
          {(control) => <TextInput {...control} type="tel" autoComplete="off" {...register('phone')} />}
        </Field>
      </FormRow>
    </>
  )
}
