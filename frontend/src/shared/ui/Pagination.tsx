import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { PAGE_SIZES } from '../api/types'
import { Button } from './Button'
import styles from './Pagination.module.css'
import { Select } from './Select'

interface PaginationProps {
  /** Zero-based, as returned by the API. */
  page: number
  size: number
  totalElements: number
  onPageChange: (page: number) => void
  onSizeChange?: (size: number) => void
}

/** "21–40 of 137", previous/next, page size (design-system.md §11.3). */
export function Pagination({ page, size, totalElements, onPageChange, onSizeChange }: PaginationProps) {
  const { t, i18n } = useTranslation()
  const sizeLabelId = useId()
  const number = new Intl.NumberFormat(i18n.language)
  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, totalElements)
  const lastPage = Math.max(0, Math.ceil(totalElements / size) - 1)

  return (
    <nav className={styles.pagination} aria-label={t('pagination.label')}>
      <p className={styles.range} aria-live="polite">
        {t('pagination.range', { from: number.format(from), to: number.format(to), total: number.format(totalElements) })}
      </p>
      <div className={styles.controls}>
        {onSizeChange && (
          <div className={styles.size}>
            <span id={sizeLabelId} className={styles.sizeLabel}>
              {t('pagination.pageSize')}
            </span>
            <div className={styles.sizeSelect}>
              <Select
                aria-labelledby={sizeLabelId}
                value={String(size)}
                onValueChange={(value) => onSizeChange(Number(value))}
                options={PAGE_SIZES.map((option) => ({ value: String(option), label: String(option) }))}
              />
            </div>
          </div>
        )}
        <Button size="sm" variant="ghost" icon={<ChevronLeft />} disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          {t('actions.previous')}
        </Button>
        <Button size="sm" variant="ghost" disabled={page >= lastPage} onClick={() => onPageChange(page + 1)}>
          {t('actions.next')}
          <ChevronRight />
        </Button>
      </div>
    </nav>
  )
}
