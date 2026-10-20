import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Alert } from '../../shared/ui/Alert'
import { Badge } from '../../shared/ui/Badge'
import { Button } from '../../shared/ui/Button'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Field } from '../../shared/ui/Field'
import { TextArea } from '../../shared/ui/Input'
import { Section } from '../../shared/ui/Page'
import { Pagination } from '../../shared/ui/Pagination'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { NOTES_PAGE_SIZE, personName, useMedicalNotes, useRemoveNote, useSaveNote, type MedicalNote } from './api'
import { EntryDialog } from './EntryDialog'
import { EntryList } from './EntryList'
import styles from './MedicalRecord.module.css'
import { noteSchema, type NoteValues } from './schemas'

/** General notes, newest first and paginated. Only the author edits or removes a note (D-037). */
export function NotesSection({ patientId, editable }: { patientId: string; editable: boolean }) {
  const { t } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const [page, setPage] = useState(0)
  const notes = useMedicalNotes(patientId, page)
  const remove = useRemoveNote(patientId)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<string | null>(null)
  const find = (id: string | null) => notes.data?.content.find((note) => note.id === id) ?? null
  const editing = find(editingId)
  const removing = find(removingId)

  const confirmRemove = () => {
    if (!removing) return
    remove.mutate(removing.id, {
      onSuccess: () => toast(t('notes.removed')),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setRemovingId(null),
    })
  }

  const content = notes.isError ? (
    <ErrorState error={notes.error} onRetry={() => void notes.refetch()} retrying={notes.isFetching} />
  ) : !notes.data ? (
    <LoadingRegion label={tCommon('states.loading')}>
      <Skeleton height="var(--space-16)" />
    </LoadingRegion>
  ) : (
    <>
      <EntryList
        label={t('notes.list')}
        emptyText={t('notes.empty')}
        entries={notes.data.content.map((note) => {
          const date = format.dateTime(note.createdAt)
          const author = note.author ? personName(note.author) : t('notes.unknownAuthor')
          return {
            id: note.id,
            name: date,
            title: (
              <>
                {t('notes.byOn', { author, date })}
                {note.edited && <Badge>{t('notes.edited')}</Badge>}
              </>
            ),
            body: note.content,
            changeable: editable && note.editable,
            editLabel: t('notes.editAction', { date }),
            removeLabel: t('notes.removeAction', { date }),
          }
        })}
        onEdit={setEditingId}
        onRemove={setRemovingId}
      />
      {notes.data.totalElements > NOTES_PAGE_SIZE && (
        <Pagination page={notes.data.page} size={notes.data.size} totalElements={notes.data.totalElements} onPageChange={setPage} />
      )}
    </>
  )

  return (
    <Section title={t('notes.title')} description={t('notes.description')}>
      {editable && <NewNoteForm patientId={patientId} onAdded={() => setPage(0)} />}
      {content}
      {editing && <EditNoteDialog patientId={patientId} note={editing} onClose={() => setEditingId(null)} />}
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemovingId(null)}
        title={t('notes.removeTitle')}
        consequence={t('remove.consequence')}
        confirmLabel={t('actions.removeConfirm')}
        onConfirm={confirmRemove}
        destructive
        pending={remove.isPending}
      />
    </Section>
  )
}

function NewNoteForm({ patientId, onAdded }: { patientId: string; onAdded: () => void }) {
  const { t } = useTranslation('medicalRecords')
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const save = useSaveNote(patientId)
  const { register, handleSubmit, formState, reset, setError, setFocus } = useForm<NoteValues>({
    resolver: standardSchemaResolver(noteSchema),
    defaultValues: { content: '' },
  })

  const onSubmit = handleSubmit((values) =>
    save.mutate(
      { content: values.content },
      {
        onSuccess: () => {
          toast(t('notes.added'))
          reset({ content: '' })
          onAdded()
        },
        onError: (error) => applyFieldErrors(error, setError, setFocus),
      },
    ),
  )

  return (
    <form className={styles.noteForm} onSubmit={onSubmit} noValidate>
      {save.error && !hasFieldErrors(save.error) && <Alert tone="danger">{errorMessage(save.error)}</Alert>}
      <Field label={t('notes.new')} error={fieldError(formState.errors.content?.message)}>
        {(props) => <TextArea rows={3} {...props} {...register('content')} />}
      </Field>
      <div className={styles.noteFormActions}>
        <Button type="submit" loading={save.isPending}>
          {t('notes.add')}
        </Button>
      </div>
    </form>
  )
}

function EditNoteDialog({ patientId, note, onClose }: { patientId: string; note: MedicalNote; onClose: () => void }) {
  const { t } = useTranslation('medicalRecords')
  const toast = useToast()
  const fieldError = useFieldErrorMessage()
  const save = useSaveNote(patientId)
  const { register, handleSubmit, formState, setError, setFocus } = useForm<NoteValues>({
    resolver: standardSchemaResolver(noteSchema),
    defaultValues: { content: note.content },
  })

  const onSubmit = handleSubmit((values) =>
    save.mutate(
      { id: note.id, version: note.version, content: values.content },
      {
        onSuccess: () => {
          toast(t('saved'))
          onClose()
        },
        onError: (error) => applyFieldErrors(error, setError, setFocus),
      },
    ),
  )

  return (
    <EntryDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('notes.editTitle')}
      onSubmit={onSubmit}
      pending={save.isPending}
      error={save.error}
    >
      <Field label={t('notes.title')} error={fieldError(formState.errors.content?.message)}>
        {(props) => <TextArea rows={6} {...props} {...register('content')} />}
      </Field>
    </EntryDialog>
  )
}
