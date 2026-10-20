import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import type { UseMutationResult } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { isApiError } from '../../shared/api/ApiError'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDocumentTitle } from '../../shared/hooks/useDocumentTitle'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { PasswordInput } from '../../shared/ui/Input'
import { useAcceptInvitation, useResetPassword, type TokenPasswordRequest } from './api'
import styles from './AuthPage.module.css'
import { PASSWORD_MIN_LENGTH, setPasswordSchema, type SetPasswordValues } from './schemas'
import { useFragmentToken } from './useFragmentToken'

type Mode = 'reset' | 'invitation'

interface SetPasswordFormProps {
  mode: Mode
  mutation: UseMutationResult<void, Error, TokenPasswordRequest>
}

function SetPasswordForm({ mode, mutation }: SetPasswordFormProps) {
  const { t } = useTranslation('auth')
  const token = useFragmentToken()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const { register, handleSubmit, formState } = useForm<SetPasswordValues>({
    resolver: standardSchemaResolver(setPasswordSchema),
    mode: 'onTouched',
  })
  useDocumentTitle(t(`${mode}.title`))

  const tokenRejected = isApiError(mutation.error) && mutation.error.code === 'INVALID_TOKEN'

  return (
    <>
      <div className={styles.heading}>
        <h1 className={styles.title}>{t(`${mode}.title`)}</h1>
        {!mutation.isSuccess && <p className={styles.lead}>{t(`${mode}.lead`)}</p>}
      </div>
      {mutation.isSuccess ? (
        <Alert tone="success" title={t(`${mode}.doneTitle`)}>
          {t(`${mode}.doneDescription`)}
        </Alert>
      ) : !token || tokenRejected ? (
        <Alert tone="danger" title={t('invalidLink.title')}>
          {t(`invalidLink.${mode}`)}
        </Alert>
      ) : (
        <form
          className={styles.form}
          onSubmit={handleSubmit((values) => mutation.mutate({ token, ...values }))}
          noValidate
        >
          {mutation.error && <Alert tone="danger">{errorMessage(mutation.error)}</Alert>}
          <Field
            label={t('fields.newPassword')}
            helper={t('passwordHint', { min: PASSWORD_MIN_LENGTH })}
            error={fieldError(formState.errors.newPassword?.message)}
          >
            {(control) => <PasswordInput {...control} autoComplete="new-password" autoFocus {...register('newPassword')} />}
          </Field>
          <Field label={t('fields.confirmPassword')} error={fieldError(formState.errors.confirmPassword?.message)}>
            {(control) => <PasswordInput {...control} autoComplete="new-password" {...register('confirmPassword')} />}
          </Field>
          <Button type="submit" variant="primary" fullWidth loading={mutation.isPending}>
            {t(`${mode}.submit`)}
          </Button>
        </form>
      )}
      <div className={styles.links}>
        <Link className={styles.link} to={tokenRejected && mode === 'reset' ? '/forgot-password' : '/login'}>
          {mutation.isSuccess ? t('goToLogin') : tokenRejected && mode === 'reset' ? t('requestNewLink') : t('backToLogin')}
        </Link>
      </div>
    </>
  )
}

export function ResetPasswordPage() {
  return <SetPasswordForm mode="reset" mutation={useResetPassword()} />
}

export function AcceptInvitationPage() {
  return <SetPasswordForm mode="invitation" mutation={useAcceptInvitation()} />
}
