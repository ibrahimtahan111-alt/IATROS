import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Badge } from '../../shared/ui/Badge'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextArea, TextInput } from '../../shared/ui/Input'
import { FormRow } from '../../shared/ui/Page'
import { useSessionUser } from '../auth/session'
import type { Medication } from './api'
import { EntryDialog } from './EntryDialog'
import styles from './MedicalRecord.module.css'
import { RecordSection, type EntryDialogState, type SectionProps } from './RecordSection'
import { medicationSchema, medicationValuesOf, toMedicationRequest, type MedicationValues } from './schemas'
import { useEntrySave } from './useEntrySave'

export function MedicationsSection({ patientId, entries, editable }: SectionProps<Medication>) {
  const { t } = useTranslation('medicalRecords')
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)

  const period = (medication: Medication) => {
    const from = medication.startDate && format.localDate(medication.startDate)
    const to = medication.endDate && format.localDate(medication.endDate)
    if (from && to) return t('medications.period', { from, to })
    if (from) return t('medications.since', { date: from })
    if (to) return t('medications.until', { date: to })
    return null
  }

  return (
    <RecordSection
      patientId={patientId}
      section="medications"
      title={t('medications.title')}
      emptyText={t('medications.empty')}
      entries={entries}
      editable={editable}
      view={(medication) => ({
        id: medication.id,
        name: medication.name,
        title: (
          <>
            {medication.name}
            <Badge tone={medication.active ? 'success' : 'neutral'}>
              {t(medication.active ? 'medications.active' : 'medications.stopped')}
            </Badge>
          </>
        ),
        meta: [medication.dosage, medication.frequency, period(medication)].filter(Boolean).join(' · ') || null,
        body: medication.notes,
      })}
      renderDialog={(state) => <MedicationDialog patientId={patientId} {...state} />}
    />
  )
}

function MedicationDialog({ patientId, entry, onClose }: EntryDialogState<Medication> & { patientId: string }) {
  const { t } = useTranslation('medicalRecords')
  const fieldError = useFieldErrorMessage()
  const form = useForm<MedicationValues>({
    resolver: standardSchemaResolver(medicationSchema),
    defaultValues: medicationValuesOf(entry),
    mode: 'onTouched',
  })
  const { register, handleSubmit, formState } = form
  const save = useEntrySave(patientId, 'medications', entry, form, onClose)

  return (
    <EntryDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t(entry ? 'medications.editTitle' : 'medications.addTitle')}
      onSubmit={handleSubmit((values) => save.submit(toMedicationRequest(values)))}
      pending={save.pending}
      error={save.error}
    >
      <Field label={t('medications.fields.name')} error={fieldError(formState.errors.name?.message)}>
        {(props) => <TextInput {...props} {...register('name')} />}
      </Field>
      <FormRow>
        <Field label={t('medications.fields.dosage')} optional error={fieldError(formState.errors.dosage?.message)}>
          {(props) => <TextInput {...props} {...register('dosage')} />}
        </Field>
        <Field
          label={t('medications.fields.frequency')}
          optional
          error={fieldError(formState.errors.frequency?.message)}
        >
          {(props) => <TextInput {...props} {...register('frequency')} />}
        </Field>
      </FormRow>
      <FormRow>
        <Field
          label={t('medications.fields.startDate')}
          optional
          error={fieldError(formState.errors.startDate?.message)}
        >
          {(props) => <DateInput {...props} {...register('startDate')} />}
        </Field>
        <Field label={t('medications.fields.endDate')} optional error={fieldError(formState.errors.endDate?.message)}>
          {(props) => <DateInput {...props} {...register('endDate')} />}
        </Field>
      </FormRow>
      <label className={styles.checkbox}>
        <input type="checkbox" {...register('active')} />
        {t('medications.fields.active')}
      </label>
      <Field label={t('medications.fields.notes')} optional error={fieldError(formState.errors.notes?.message)}>
        {(props) => <TextArea rows={3} {...props} {...register('notes')} />}
      </Field>
    </EntryDialog>
  )
}
