import { cx } from '../lib/cx'
import styles from './Spinner.module.css'

interface SpinnerProps {
  size?: 'sm' | 'md'
  className?: string
}

/** Decorative progress indicator; the surrounding element carries the accessible busy state. */
export function Spinner({ size = 'sm', className }: SpinnerProps) {
  return <span className={cx(styles.spinner, styles[size], className)} aria-hidden="true" />
}
