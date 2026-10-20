import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { Badge } from '../../shared/ui/Badge'
import { Field } from '../../shared/ui/Field'
import { TextArea, TextInput } from '../../shared/ui/Input'
import { Select } from '../../shared/ui/Select'
import { SEVERITIES, type Allergy, type AllergySeverity } from './api'
import { EntryDialog } from './EntryDialog'
import { RecordSection, type EntryDialogState, type SectionProps } from './RecordSection'
import { allergySchema, allergyValuesOf, toAllergyRequest, type AllergyValues } from './schemas'
import { useEntrySave } from './useEntrySave'

const SEVERITY_TONE: Record<AllergySeverity, 'neutral' | 'warning' | 'danger'> = {
  MILD: 'neutral',
  MODERATE: 'warning',
  SEVERE: 'danger',
  UNKNOWN: 'neutral',
}

export function AllergiesSection({ patientId, entries, editable }: SectionProps<Allergy>) {
  const { t } = useTranslation('medicalRecords')
  return (
    <RecordSection
      patientId={patientId}
      section="allergies"
      title={t('allergies.title')}
      emptyText={t('allergies.empty')}
      entries={entries}
      editable={editable}
      view={(allergy) => ({
        id: allergy.id,
        name: allergy.allergen,
        title: (
          <>
            {allergy.allergen}
            {allergy.severity && (
              <Badge tone={SEVERITY_TONE[allergy.severity]}>{t(`allergies.severity.${allergy.severity}`)}</Badge>
            )}
          </>
        ),
        meta: allergy.reaction,
        body: allergy.notes,
      })}
      renderDialog={(state) => <AllergyDialog patientId={patientId} {...state} />}
    />
  )
}

function AllergyDialog({ patientId, entry, onClose }: EntryDialogState<Allergy> & { patientId: string }) {
  const { t } = useTranslation('medicalRecords')
  const fieldError = useFieldErrorMessage()
  const form = useForm<AllergyValues>({
    resolver: standardSchemaResolver(allergySchema),
    defaultValues: allergyValuesOf(entry),
    mode: 'onTouched',
  })
  const { control, register, handleSubmit, formState } = form
  const save = useEntrySave(patientId, 'allergies', entry, form, onClose)

  return (
    <EntryDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t(entry ? 'allergies.editTitle' : 'allergies.addTitle')}
      onSubmit={handleSubmit((values) => save.submit(toAllergyRequest(values)))}
      pending={save.pending}
      error={save.error}
    >
      <Field label={t('allergies.fields.allergen')} error={fieldError(formState.errors.allergen?.message)}>
        {(props) => <TextInput {...props} {...register('allergen')} />}
      </Field>
      <Field label={t('allergies.fields.severity')} optional>
        {(props) => (
          <Controller
            control={control}
            name="severity"
            render={({ field }) => (
              <Select
                {...props}
                name={field.name}
                value={field.value || undefined}
                onValueChange={field.onChange}
                placeholder={t('allergies.severityPlaceholder')}
                options={SEVERITIES.map((severity) => ({ value: severity, label: t(`allergies.severity.${severity}`) }))}
              />
            )}
          />
        )}
      </Field>
      <Field label={t('allergies.fields.reaction')} optional error={fieldError(formState.errors.reaction?.message)}>
        {(props) => <TextInput {...props} {...register('reaction')} />}
      </Field>
      <Field label={t('allergies.fields.notes')} optional error={fieldError(formState.errors.notes?.message)}>
        {(props) => <TextArea rows={3} {...props} {...register('notes')} />}
      </Field>
    </EntryDialog>
  )
}
