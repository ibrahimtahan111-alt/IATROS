import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { isApiError } from '../../shared/api/ApiError'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { PasswordInput } from '../../shared/ui/Input'
import { useToast } from '../../shared/ui/useToast'
import { useChangePassword } from '../auth/api'
import { PASSWORD_MIN_LENGTH, changePasswordSchema, type ChangePasswordValues } from '../auth/schemas'
import styles from './AccountPage.module.css'

export function ChangePasswordForm() {
  const { t } = useTranslation('account')
  const toast = useToast()
  const changePassword = useChangePassword()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const { register, handleSubmit, formState, reset, setError, setFocus } = useForm<ChangePasswordValues>({
    resolver: standardSchemaResolver(changePasswordSchema),
    mode: 'onTouched',
  })

  const onSubmit = handleSubmit((values) =>
    changePassword.mutate(values, {
      onSuccess: () => {
        reset()
        toast(t('password.changed'))
      },
      onError: (error) => {
        if (isApiError(error) && error.fieldErrors.length > 0) {
          for (const { field, code } of error.fieldErrors) {
            setError(field as keyof ChangePasswordValues, { message: code })
          }
          setFocus(error.fieldErrors[0]?.field as keyof ChangePasswordValues)
        }
      },
    }),
  )

  const showGeneralError = changePassword.error && !(isApiError(changePassword.error) && changePassword.error.fieldErrors.length > 0)

  return (
    <form className={styles.form} onSubmit={onSubmit} noValidate>
      {showGeneralError && <Alert tone="danger">{errorMessage(changePassword.error)}</Alert>}
      <Field label={t('password.current')} error={fieldError(formState.errors.currentPassword?.message)}>
        {(control) => <PasswordInput {...control} autoComplete="current-password" {...register('currentPassword')} />}
      </Field>
      <Field
        label={t('password.new')}
        helper={t('password.hint', { min: PASSWORD_MIN_LENGTH })}
        error={fieldError(formState.errors.newPassword?.message)}
      >
        {(control) => <PasswordInput {...control} autoComplete="new-password" {...register('newPassword')} />}
      </Field>
      <Field label={t('password.confirm')} error={fieldError(formState.errors.confirmPassword?.message)}>
        {(control) => <PasswordInput {...control} autoComplete="new-password" {...register('confirmPassword')} />}
      </Field>
      <p className={styles.note}>{t('password.otherSessions')}</p>
      <div>
        <Button type="submit" variant="primary" loading={changePassword.isPending}>
          {t('password.submit')}
        </Button>
      </div>
    </form>
  )
}
