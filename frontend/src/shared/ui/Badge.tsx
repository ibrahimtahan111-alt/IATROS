import type { ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './Badge.module.css'

export type BadgeTone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'info'

/** Small label for categories and counts (role, specialty, "2 allergies"). Always readable text. */
export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return <span className={cx(styles.badge, styles[tone])}>{children}</span>
}
