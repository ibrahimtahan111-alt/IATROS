import { cx } from '../lib/cx'
import styles from './SegmentedControl.module.css'

interface SegmentedControlProps<T extends string> {
  /** Accessible name of the group. */
  label: string
  value: T
  options: readonly { value: T; label: string }[]
  onValueChange: (value: T) => void
}

/** A small set of mutually exclusive views (e.g. Day / Week / Month); the current one is marked as pressed. */
export function SegmentedControl<T extends string>({ label, value, options, onValueChange }: SegmentedControlProps<T>) {
  return (
    <div role="group" aria-label={label} className={styles.group}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          className={cx(styles.segment, option.value === value && styles.selected)}
          onClick={() => onValueChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
