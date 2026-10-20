import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useMemo } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { todayIn } from '../../shared/lib/localDates'
import { Badge } from '../../shared/ui/Badge'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextArea, TextInput } from '../../shared/ui/Input'
import { FormRow } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { useSessionUser } from '../auth/session'
import { CONDITION_STATUSES, type Condition } from './api'
import { EntryDialog } from './EntryDialog'
import { RecordSection, type EntryDialogState, type SectionProps } from './RecordSection'
import { conditionSchema, conditionValuesOf, toConditionRequest, type ConditionValues } from './schemas'
import { useEntrySave } from './useEntrySave'

export function ConditionsSection({ patientId, entries, editable }: SectionProps<Condition>) {
  const { t } = useTranslation('medicalRecords')
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  return (
    <RecordSection
      patientId={patientId}
      section="conditions"
      title={t('conditions.title')}
      emptyText={t('conditions.empty')}
      entries={entries}
      editable={editable}
      view={(condition) => ({
        id: condition.id,
        name: condition.name,
        title: (
          <>
            {condition.name}
            <Badge tone={condition.status === 'ACTIVE' ? 'accent' : 'neutral'}>
              {t(`conditions.status.${condition.status}`)}
            </Badge>
          </>
        ),
        meta: condition.diagnosedOn && t('conditions.diagnosed', { date: format.localDate(condition.diagnosedOn) }),
        body: condition.description,
      })}
      renderDialog={(state) => <ConditionDialog patientId={patientId} {...state} />}
    />
  )
}

function ConditionDialog({ patientId, entry, onClose }: EntryDialogState<Condition> & { patientId: string }) {
  const { t } = useTranslation('medicalRecords')
  const fieldError = useFieldErrorMessage()
  const user = useSessionUser()
  const today = todayIn(user.clinic.timeZone)
  const schema = useMemo(() => conditionSchema(today), [today])
  const form = useForm<ConditionValues>({
    resolver: standardSchemaResolver(schema),
    defaultValues: conditionValuesOf(entry),
    mode: 'onTouched',
  })
  const { control, register, handleSubmit, formState } = form
  const save = useEntrySave(patientId, 'conditions', entry, form, onClose)

  return (
    <EntryDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t(entry ? 'conditions.editTitle' : 'conditions.addTitle')}
      onSubmit={handleSubmit((values) => save.submit(toConditionRequest(values)))}
      pending={save.pending}
      error={save.error}
    >
      <Field label={t('conditions.fields.name')} error={fieldError(formState.errors.name?.message)}>
        {(props) => <TextInput {...props} {...register('name')} />}
      </Field>
      <FormRow>
        <Field label={t('conditions.fields.status')}>
          {(props) => (
            <Controller
              control={control}
              name="status"
              render={({ field }) => (
                <Select
                  {...props}
                  name={field.name}
                  value={field.value}
                  onValueChange={field.onChange}
                  options={CONDITION_STATUSES.map((status) => ({ value: status, label: t(`conditions.status.${status}`) }))}
                />
              )}
            />
          )}
        </Field>
        <Field
          label={t('conditions.fields.diagnosedOn')}
          optional
          error={fieldError(formState.errors.diagnosedOn?.message)}
        >
          {(props) => <DateInput {...props} max={today} {...register('diagnosedOn')} />}
        </Field>
      </FormRow>
      <Field
        label={t('conditions.fields.description')}
        optional
        error={fieldError(formState.errors.description?.message)}
      >
        {(props) => <TextArea rows={3} {...props} {...register('description')} />}
      </Field>
    </EntryDialog>
  )
}
