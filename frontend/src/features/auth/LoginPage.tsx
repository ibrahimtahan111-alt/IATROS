import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDocumentTitle } from '../../shared/hooks/useDocumentTitle'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { PasswordInput, TextInput } from '../../shared/ui/Input'
import { useLogin } from './api'
import styles from './AuthPage.module.css'
import { loginSchema } from './schemas'
import { useSchemaForm } from './useSchemaForm'

export function LoginPage() {
  const { t } = useTranslation('auth')
  const login = useLogin()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const { errors, handleSubmit, handleChange } = useSchemaForm(loginSchema)

  useDocumentTitle(t('login.title'))

  // On success the session cache updates and RedirectIfAuthenticated moves on to the requested page.
  const onSubmit = handleSubmit((values) => login.mutate(values))

  return (
    <>
      <div className={styles.heading}>
        <h1 className={styles.title}>{t('login.title')}</h1>
        <p className={styles.lead}>{t('login.lead')}</p>
      </div>
      <form className={styles.form} onSubmit={onSubmit} onChange={handleChange} noValidate>
        {login.error && <Alert tone="danger">{errorMessage(login.error)}</Alert>}
        <Field label={t('fields.email')} error={fieldError(errors.email)}>
          {(control) => <TextInput {...control} name="email" type="email" autoComplete="username" autoFocus />}
        </Field>
        <Field label={t('fields.password')} error={fieldError(errors.password)}>
          {(control) => <PasswordInput {...control} name="password" autoComplete="current-password" />}
        </Field>
        <Button type="submit" variant="primary" fullWidth loading={login.isPending}>
          {t('login.submit')}
        </Button>
      </form>
      <div className={styles.links}>
        <Link className={styles.link} to="/forgot-password">
          {t('login.forgotPassword')}
        </Link>
      </div>
    </>
  )
}
