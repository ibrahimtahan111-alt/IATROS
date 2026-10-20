import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './Button.module.css'
import { Spinner } from './Spinner'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: 'md' | 'sm'
  /** Shows progress, keeps the label (so the width does not jump), and blocks further clicks. */
  loading?: boolean
  icon?: ReactNode
  fullWidth?: boolean
}

export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  fullWidth = false,
  type = 'button',
  disabled,
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={cx(styles.button, styles[variant], styles[size], fullWidth && styles.fullWidth, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner /> : icon}
      {children}
    </button>
  )
}
