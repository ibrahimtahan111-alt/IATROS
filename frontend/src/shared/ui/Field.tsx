import { useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import styles from './Field.module.css'

export interface FieldControlProps {
  id: string
  'aria-invalid': boolean | undefined
  'aria-describedby': string | undefined
}

interface FieldProps {
  label: string
  /** Translated error text; replaces the helper text and marks the control invalid. */
  error?: string
  helper?: string
  optional?: boolean
  children: (control: FieldControlProps) => ReactNode
}

/** Label above, helper or error below, wired for assistive technology (design-system.md §11.2). */
export function Field({ label, error, helper, optional = false, children }: FieldProps) {
  const { t } = useTranslation()
  const id = useId()
  const messageId = `${id}-message`
  const message = error ?? helper

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
        {optional && <span className={styles.optional}> {t('states.optional')}</span>}
      </label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': message ? messageId : undefined })}
      {message && (
        <p id={messageId} className={error ? styles.error : styles.helper}>
          {message}
        </p>
      )}
    </div>
  )
}
