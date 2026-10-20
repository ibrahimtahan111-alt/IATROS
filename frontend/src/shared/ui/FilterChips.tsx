import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import styles from './FilterChips.module.css'

export interface ActiveFilter {
  key: string
  /** Translated field name, e.g. "Doctor". */
  label: string
  /** Translated display value, e.g. "Dr. Martin". */
  value: string
}

interface FilterChipsProps {
  filters: readonly ActiveFilter[]
  onRemove: (key: string) => void
  onClearAll: () => void
}

/** Active filters as removable chips, with "Clear all" whenever any filter is set (design-system.md §11.4). */
export function FilterChips({ filters, onRemove, onClearAll }: FilterChipsProps) {
  const { t } = useTranslation()
  if (filters.length === 0) {
    return null
  }

  return (
    <ul className={styles.chips} aria-label={t('filters.active')}>
      {filters.map((filter) => (
        <li key={filter.key} className={styles.chip}>
          <span>
            <span className={styles.chipLabel}>{filter.label}:</span> {filter.value}
          </span>
          <button
            type="button"
            className={styles.remove}
            aria-label={t('filters.remove', { filter: `${filter.label}: ${filter.value}` })}
            onClick={() => onRemove(filter.key)}
          >
            <X aria-hidden="true" />
          </button>
        </li>
      ))}
      <li>
        <Button size="sm" variant="ghost" onClick={onClearAll}>
          {t('filters.clearAll')}
        </Button>
      </li>
    </ul>
  )
}
