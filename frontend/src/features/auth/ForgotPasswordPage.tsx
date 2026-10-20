import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDocumentTitle } from '../../shared/hooks/useDocumentTitle'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { TextInput } from '../../shared/ui/Input'
import { useForgotPassword } from './api'
import styles from './AuthPage.module.css'
import { forgotPasswordSchema } from './schemas'
import { useSchemaForm } from './useSchemaForm'

/** The confirmation is identical whether or not the email has an account (security.md §7). */
export function ForgotPasswordPage() {
  const { t } = useTranslation('auth')
  const forgotPassword = useForgotPassword()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  useDocumentTitle(t('forgotPassword.title'))
  const { errors, handleSubmit, handleChange } = useSchemaForm(forgotPasswordSchema)

  return (
    <>
      <div className={styles.heading}>
        <h1 className={styles.title}>{t('forgotPassword.title')}</h1>
        <p className={styles.lead}>{t('forgotPassword.lead')}</p>
      </div>
      {forgotPassword.isSuccess ? (
        <Alert tone="success" title={t('forgotPassword.sentTitle')}>
          {t('forgotPassword.sentDescription')}
        </Alert>
      ) : (
        <form
          className={styles.form}
          onSubmit={handleSubmit((values) => forgotPassword.mutate(values.email))}
          onChange={handleChange}
          noValidate
        >
          {forgotPassword.error && <Alert tone="danger">{errorMessage(forgotPassword.error)}</Alert>}
          <Field label={t('fields.email')} error={fieldError(errors.email)}>
            {(control) => <TextInput {...control} name="email" type="email" autoComplete="email" autoFocus />}
          </Field>
          <Button type="submit" variant="primary" fullWidth loading={forgotPassword.isPending}>
            {t('forgotPassword.submit')}
          </Button>
        </form>
      )}
      <div className={styles.links}>
        <Link className={styles.link} to="/login">
          {t('backToLogin')}
        </Link>
      </div>
    </>
  )
}
