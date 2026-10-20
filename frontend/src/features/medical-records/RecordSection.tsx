import { Plus } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { Button } from '../../shared/ui/Button'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { Section } from '../../shared/ui/Page'
import { useToast } from '../../shared/ui/useToast'
import { useRemoveEntry, type RecordSection as SectionName } from './api'
import { EntryList, type EntryView } from './EntryList'

/** Props of each concrete section component. */
export interface SectionProps<T> {
  patientId: string
  entries: readonly T[]
  editable: boolean
}

export interface EntryDialogState<T> {
  /** Undefined when adding. */
  entry: T | undefined
  onClose: () => void
}

interface RecordSectionProps<T extends { id: string }> {
  patientId: string
  section: SectionName
  title: string
  emptyText: string
  entries: readonly T[]
  view: (entry: T) => EntryView
  /** False for archived patients: the record is read-only. */
  editable: boolean
  /** The add/edit dialog; mounted only while open, so each opening starts from the entry's current values. */
  renderDialog: (state: EntryDialogState<T>) => ReactNode
}

const NEW = 'new'

/** One structured section of the medical record (allergies, conditions, medications, history). */
export function RecordSection<T extends { id: string }>({
  patientId,
  section,
  title,
  emptyText,
  entries,
  view,
  editable,
  renderDialog,
}: RecordSectionProps<T>) {
  const { t } = useTranslation('medicalRecords')
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const remove = useRemoveEntry(patientId, section)
  // IDs, not snapshots: after a refetch (e.g. a conflict) the dialog sends the current version (D-043).
  const [editingId, setEditingId] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<string | null>(null)
  const find = (id: string | null) => entries.find((entry) => entry.id === id) ?? null
  const editing = find(editingId)
  const removing = find(removingId)

  const confirmRemove = () => {
    if (!removing) return
    const name = view(removing).name
    remove.mutate(removing.id, {
      onSuccess: () => toast(t('remove.done', { name })),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setRemovingId(null),
    })
  }

  return (
    <Section
      title={title}
      action={
        editable && (
          <Button size="sm" icon={<Plus aria-hidden="true" />} onClick={() => setEditingId(NEW)}>
            {t('actions.add')}
          </Button>
        )
      }
    >
      <EntryList
        label={title}
        entries={entries.map(view)}
        emptyText={emptyText}
        onEdit={editable ? setEditingId : undefined}
        onRemove={editable ? setRemovingId : undefined}
      />
      {(editingId === NEW || editing) && renderDialog({ entry: editing ?? undefined, onClose: () => setEditingId(null) })}
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemovingId(null)}
        title={removing ? t('remove.title', { name: view(removing).name }) : ''}
        consequence={t('remove.consequence')}
        confirmLabel={t('actions.removeConfirm')}
        onConfirm={confirmRemove}
        destructive
        pending={remove.isPending}
      />
    </Section>
  )
}
