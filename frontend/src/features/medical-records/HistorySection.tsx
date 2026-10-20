import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { todayIn } from '../../shared/lib/localDates'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextArea } from '../../shared/ui/Input'
import { useSessionUser } from '../auth/session'
import type { HistoryEntry } from './api'
import { EntryDialog } from './EntryDialog'
import { RecordSection, type EntryDialogState, type SectionProps } from './RecordSection'
import { historySchema, historyValuesOf, toHistoryRequest, type HistoryValues } from './schemas'
import { useEntrySave } from './useEntrySave'

export function HistorySection({ patientId, entries, editable }: SectionProps<HistoryEntry>) {
  const { t } = useTranslation('medicalRecords')
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  return (
    <RecordSection
      patientId={patientId}
      section="history"
      title={t('history.title')}
      emptyText={t('history.empty')}
      entries={entries}
      editable={editable}
      view={(entry) => ({
        id: entry.id,
        name: entry.description,
        title: entry.description,
        meta: entry.occurredOn ? format.localDate(entry.occurredOn) : t('history.undated'),
        body: entry.notes,
      })}
      renderDialog={(state) => <HistoryDialog patientId={patientId} {...state} />}
    />
  )
}

function HistoryDialog({ patientId, entry, onClose }: EntryDialogState<HistoryEntry> & { patientId: string }) {
  const { t } = useTranslation('medicalRecords')
  const fieldError = useFieldErrorMessage()
  const user = useSessionUser()
  const today = todayIn(user.clinic.timeZone)
  const schema = useMemo(() => historySchema(today), [today])
  const form = useForm<HistoryValues>({
    resolver: standardSchemaResolver(schema),
    defaultValues: historyValuesOf(entry),
    mode: 'onTouched',
  })
  const { register, handleSubmit, formState } = form
  const save = useEntrySave(patientId, 'history', entry, form, onClose)

  return (
    <EntryDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t(entry ? 'history.editTitle' : 'history.addTitle')}
      onSubmit={handleSubmit((values) => save.submit(toHistoryRequest(values)))}
      pending={save.pending}
      error={save.error}
    >
      <Field label={t('history.fields.description')} error={fieldError(formState.errors.description?.message)}>
        {(props) => <TextArea rows={2} {...props} {...register('description')} />}
      </Field>
      <Field label={t('history.fields.occurredOn')} optional error={fieldError(formState.errors.occurredOn?.message)}>
        {(props) => <DateInput {...props} max={today} {...register('occurredOn')} />}
      </Field>
      <Field label={t('history.fields.notes')} optional error={fieldError(formState.errors.notes?.message)}>
        {(props) => <TextArea rows={3} {...props} {...register('notes')} />}
      </Field>
    </EntryDialog>
  )
}
