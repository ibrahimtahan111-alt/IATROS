import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import type { HTMLAttributes, ReactNode, TdHTMLAttributes, ThHTMLAttributes } from 'react'
import type { SortState } from '../api/types'
import { cx } from '../lib/cx'
import styles from './Table.module.css'

/**
 * Calm data table (design-system.md §11.3): hairline rows, sticky header, no zebra stripes or vertical borders.
 * Wrap it with a toolbar above and {@link Pagination} below.
 */
export function Table({ className, caption, children, ...rest }: HTMLAttributes<HTMLTableElement> & { caption: string }) {
  return (
    <div className={styles.scroll}>
      <table className={cx(styles.table, className)} {...rest}>
        <caption className="visually-hidden">{caption}</caption>
        {children}
      </table>
    </div>
  )
}

export function Th({ className, ...rest }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th scope="col" className={cx(styles.th, className)} {...rest} />
}

export function Td({ className, primary = false, ...rest }: TdHTMLAttributes<HTMLTableCellElement> & { primary?: boolean }) {
  return <td className={cx(styles.td, primary && styles.primary, className)} {...rest} />
}

interface SortableThProps {
  field: string
  sort: SortState | null
  onSortChange: (sort: SortState) => void
  children: ReactNode
}

/** Column header that toggles ascending/descending; the direction icon shows on the sorted column and on hover. */
export function SortableTh({ field, sort, onSortChange, children }: SortableThProps) {
  const active = sort?.field === field
  const direction = active ? sort.direction : null
  const Icon = direction === 'asc' ? ArrowUp : direction === 'desc' ? ArrowDown : ArrowUpDown

  return (
    <th
      scope="col"
      className={styles.th}
      aria-sort={direction === 'asc' ? 'ascending' : direction === 'desc' ? 'descending' : 'none'}
    >
      <button
        type="button"
        className={cx(styles.sortButton, active && styles.sorted)}
        onClick={() => onSortChange({ field, direction: direction === 'asc' ? 'desc' : 'asc' })}
      >
        {children}
        <Icon className={styles.sortIcon} aria-hidden="true" />
      </button>
    </th>
  )
}
