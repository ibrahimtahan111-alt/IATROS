import { useId } from 'react'
import type { UseFormRegister } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { StaffRole } from './api'
import type { NewMemberValues } from './schemas'
import styles from './RoleChoice.module.css'

const ROLES: readonly StaffRole[] = ['DOCTOR', 'SECRETARY']

interface RoleChoiceProps {
  register: UseFormRegister<NewMemberValues>
  error?: string
}

/** Role as described options rather than a bare dropdown, so the owner sees what each role can do. */
export function RoleChoice({ register, error }: RoleChoiceProps) {
  const { t } = useTranslation('team')
  const { t: tCommon } = useTranslation()
  const id = useId()
  const errorId = `${id}-error`
  // Registered here, in document order, so the first invalid field receives focus on submit.
  const field = register('role')

  return (
    <fieldset className={styles.group} aria-describedby={error ? errorId : undefined}>
      <legend className="visually-hidden">{t('fields.role')}</legend>
      {ROLES.map((role) => (
        <label key={role} className={styles.option}>
          <input type="radio" value={role} className={styles.radio} {...field} />
          <span className={styles.text}>
            <span className={styles.label}>{tCommon(`roles.${role}`)}</span>
            <span className={styles.description}>{t(`roleOptions.${role}`)}</span>
          </span>
        </label>
      ))}
      {error && (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      )}
    </fieldset>
  )
}
