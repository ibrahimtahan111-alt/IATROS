import type { HTMLAttributes } from 'react'
import { cx } from '../lib/cx'
import styles from './Card.module.css'

/** Only for genuinely independent objects such as a KPI tile (design-system.md §4.3); sections use whitespace. */
export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx(styles.card, className)} {...rest} />
}
