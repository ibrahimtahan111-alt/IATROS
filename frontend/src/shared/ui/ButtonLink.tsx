import type { ReactNode } from 'react'
import { Link, type LinkProps } from 'react-router'
import { cx } from '../lib/cx'
import type { ButtonVariant } from './Button'
import styles from './Button.module.css'

interface ButtonLinkProps extends LinkProps {
  variant?: ButtonVariant
  size?: 'md' | 'sm'
  icon?: ReactNode
}

/** Navigation that looks like a button (e.g. "Add team member" opening a page). */
export function ButtonLink({ variant = 'secondary', size = 'md', icon, className, children, ...rest }: ButtonLinkProps) {
  return (
    <Link className={cx(styles.button, styles[variant], styles[size], className as string | undefined)} {...rest}>
      {icon}
      {children}
    </Link>
  )
}
