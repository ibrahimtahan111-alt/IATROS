import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './IconButton.module.css'
import { Tooltip } from './Tooltip'

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'aria-label'> {
  /** Accessible name, also shown as a tooltip (design-system.md §10). */
  label: string
  icon: ReactNode
}

export function IconButton({ label, icon, type = 'button', className, ...rest }: IconButtonProps) {
  return (
    <Tooltip content={label}>
      <button type={type} aria-label={label} className={cx(styles.iconButton, className)} {...rest}>
        {icon}
      </button>
    </Tooltip>
  )
}
