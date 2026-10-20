import { CircleCheck, CircleX, Info, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './Alert.module.css'

export type AlertTone = 'info' | 'success' | 'warning' | 'danger'

const ICONS = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleX } as const

interface AlertProps {
  tone?: AlertTone
  title?: string
  children?: ReactNode
  action?: ReactNode
  className?: string
}

/** Inline message near the action it concerns. Danger alerts are announced immediately. */
export function Alert({ tone = 'info', title, children, action, className }: AlertProps) {
  const Icon = ICONS[tone]
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={cx(styles.alert, styles[tone], className)}>
      <Icon className={styles.icon} aria-hidden="true" />
      <div className={styles.body}>
        {title && <p className={styles.title}>{title}</p>}
        {children && <div className={styles.text}>{children}</div>}
      </div>
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}
