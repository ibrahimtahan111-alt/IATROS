import { useState, type InputHTMLAttributes, type Ref, type TextareaHTMLAttributes } from 'react'
import { useTranslation } from 'react-i18next'
import { Eye, EyeOff } from 'lucide-react'
import { cx } from '../lib/cx'
import iconButtonStyles from './IconButton.module.css'
import styles from './Input.module.css'

type InputProps = InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> }

export function TextInput({ className, type = 'text', ...rest }: InputProps) {
  return <input type={type} className={cx(styles.input, className)} {...rest} />
}

/** Multi-line free text (notes). Grows vertically only, so it never breaks the form's width. */
export function TextArea({
  className,
  rows = 4,
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { ref?: Ref<HTMLTextAreaElement> }) {
  return <textarea rows={rows} className={cx(styles.input, styles.textarea, className)} {...rest} />
}

/** Native date picker: localized by the browser and keyboard accessible. Value format is YYYY-MM-DD. */
export function DateInput({ className, ...rest }: Omit<InputProps, 'type'>) {
  return <input type="date" className={cx(styles.input, 'tabular', className)} {...rest} />
}

export function PasswordInput({ className, ...rest }: Omit<InputProps, 'type'>) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)

  // A plain toggle rather than IconButton: the sign-in page must not load the tooltip machinery.
  return (
    <div className={styles.withTrailing}>
      <input type={visible ? 'text' : 'password'} className={cx(styles.input, className)} {...rest} />
      <button
        type="button"
        className={cx(iconButtonStyles.iconButton, styles.trailing)}
        aria-label={visible ? t('actions.hidePassword') : t('actions.showPassword')}
        aria-pressed={visible}
        onClick={() => setVisible((current) => !current)}
      >
        {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
      </button>
    </div>
  )
}
