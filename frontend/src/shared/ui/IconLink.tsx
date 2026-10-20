import type { AnchorHTMLAttributes, ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './IconButton.module.css'
import { Tooltip } from './Tooltip'

interface IconLinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'children' | 'aria-label'> {
  /** Accessible name, also shown as a tooltip (design-system.md §10). */
  label: string
  icon: ReactNode
  href: string
}

/** A plain link that looks like an {@link IconButton}, for non-route targets such as file downloads. */
export function IconLink({ label, icon, className, ...rest }: IconLinkProps) {
  return (
    <Tooltip content={label}>
      <a aria-label={label} className={cx(styles.iconButton, className)} {...rest}>
        {icon}
      </a>
    </Tooltip>
  )
}
