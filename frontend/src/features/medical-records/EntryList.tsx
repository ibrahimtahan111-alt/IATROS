import { Pencil, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { IconButton } from '../../shared/ui/IconButton'
import styles from './MedicalRecord.module.css'

export interface EntryView {
  id: string
  /** Plain-text name, used in action labels. */
  name: string
  title: ReactNode
  meta?: ReactNode
  /** Free text, shown as written (line breaks kept, never translated). */
  body?: string | null
  /** False hides this entry's actions (e.g. someone else's note). */
  changeable?: boolean
  editLabel?: string
  removeLabel?: string
}

interface EntryListProps {
  label: string
  entries: readonly EntryView[]
  emptyText: string
  /** Omitted when the record is read-only. */
  onEdit?: (id: string) => void
  onRemove?: (id: string) => void
}

/** The rows of one structured section of the medical record. */
export function EntryList({ label, entries, emptyText, onEdit, onRemove }: EntryListProps) {
  const { t } = useTranslation('medicalRecords')

  if (entries.length === 0) {
    return <p className={styles.empty}>{emptyText}</p>
  }
  return (
    <ul className={styles.entries} aria-label={label}>
      {entries.map((entry) => (
        <li key={entry.id} className={styles.entry}>
          <div className={styles.entryMain}>
            <p className={styles.entryTitle}>{entry.title}</p>
            {entry.meta && <p className={styles.entryMeta}>{entry.meta}</p>}
            {entry.body && <p className={styles.entryBody}>{entry.body}</p>}
          </div>
          {(onEdit || onRemove) && entry.changeable !== false && (
            <div className={styles.entryActions}>
              {onEdit && (
                <IconButton
                  label={entry.editLabel ?? t('actions.edit', { name: entry.name })}
                  icon={<Pencil />}
                  onClick={() => onEdit(entry.id)}
                />
              )}
              {onRemove && (
                <IconButton
                  label={entry.removeLabel ?? t('actions.remove', { name: entry.name })}
                  icon={<Trash2 />}
                  onClick={() => onRemove(entry.id)}
                />
              )}
            </div>
          )}
        </li>
      ))}
    </ul>
  )
}
