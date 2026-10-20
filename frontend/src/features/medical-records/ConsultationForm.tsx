import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { localDateTimeOf } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextArea, TextInput } from '../../shared/ui/Input'
import { FormActions, FormRow, Section } from '../../shared/ui/Page'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import type { Consultation, ConsultationRequest } from './consultationsApi'
import styles from './MedicalRecord.module.css'
import { consultationSchema, consultationValuesOf, toConsultationRequest, type ConsultationValues } from './schemas'

interface ConsultationFormProps {
  /** Absent when recording a new consultation. */
  consultation?: Consultation
  /** Prefills a new consultation, e.g. from the appointment it records. */
  initialReason?: string
  submitLabel: string
  successMessage: string
  cancelPath: string
  submit: (request: ConsultationRequest, callbacks: SubmitCallbacks) => void
  pending: boolean
  error: unknown
}

export interface SubmitCallbacks {
  onSuccess: (saved: Consultation) => void
  onError: (error: unknown) => void
}

/** Fields of a consultation (ui-ux.md §17). Date and time are entered in the clinic's time zone. */
export function ConsultationForm({
  consultation,
  initialReason,
  submitLabel,
  successMessage,
  cancelPath,
  submit,
  pending,
  error: saveError,
}: ConsultationFormProps) {
  const { t } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const timeZone = user.clinic.timeZone
  const navigate = useNavigate()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const schema = useMemo(() => consultationSchema(timeZone), [timeZone])
  const [savedPath, setSavedPath] = useState<string | null>(null)
  const form = useForm<ConsultationValues>({
    resolver: standardSchemaResolver(schema),
    defaultValues: {
      ...consultationValuesOf(localDateTimeOf(consultation?.consultedAt ?? new Date(), timeZone), consultation),
      ...(initialReason ? { reason: initialReason } : {}),
    },
    mode: 'onTouched',
  })
  const { register, handleSubmit, formState, setError, setFocus } = form
  const blocker = useUnsavedChangesGuard(formState.isDirty && savedPath === null)

  useEffect(() => {
    if (savedPath) {
      void navigate(savedPath, { replace: true })
    }
  }, [savedPath, navigate])

  const onSubmit = handleSubmit((values) =>
    submit(toConsultationRequest(values, timeZone), {
      onSuccess: (saved: Consultation) => {
        toast(successMessage)
        setSavedPath(`/patients/${saved.patientId}/consultations/${saved.id}`)
      },
      onError: (failure: unknown) => applyFieldErrors(failure, setError, setFocus),
    }),
  )

  const error = (name: keyof ConsultationValues) => fieldError(formState.errors[name]?.message)

  return (
    <form onSubmit={onSubmit} noValidate>
      {Boolean(saveError) && !hasFieldErrors(saveError) && (
        <Alert tone="danger" className={styles.alert}>
          {errorMessage(saveError)}
        </Alert>
      )}
      <Section title={t('consultations.title')}>
        <div className={styles.form}>
          <FormRow>
            <Field label={t('consultations.fields.consultedAt')} helper={t('consultations.help.consultedAt')} error={error('consultedAt')}>
              {(props) => <TextInput type="datetime-local" className="tabular" {...props} {...register('consultedAt')} />}
            </Field>
            <Field label={t('consultations.fields.reason')} error={error('reason')}>
              {(props) => <TextInput {...props} {...register('reason')} />}
            </Field>
          </FormRow>
          <Field label={t('consultations.fields.clinicalNotes')} optional error={error('clinicalNotes')}>
            {(props) => <TextArea rows={6} {...props} {...register('clinicalNotes')} />}
          </Field>
          <Field label={t('consultations.fields.diagnosis')} optional error={error('diagnosis')}>
            {(props) => <TextArea rows={2} {...props} {...register('diagnosis')} />}
          </Field>
          <Field label={t('consultations.fields.treatmentNotes')} optional error={error('treatmentNotes')}>
            {(props) => <TextArea rows={4} {...props} {...register('treatmentNotes')} />}
          </Field>
          <FormRow>
            <Field label={t('consultations.fields.followUpOn')} optional helper={t('consultations.help.followUpOn')} error={error('followUpOn')}>
              {(props) => <DateInput {...props} {...register('followUpOn')} />}
            </Field>
          </FormRow>
          <Field label={t('consultations.fields.followUpNotes')} optional error={error('followUpNotes')}>
            {(props) => <TextArea rows={3} {...props} {...register('followUpNotes')} />}
          </Field>
        </div>
      </Section>
      <FormActions>
        <Button type="submit" variant="primary" loading={pending}>
          {submitLabel}
        </Button>
        <ButtonLink to={cancelPath} variant="ghost">
          {tCommon('actions.cancel')}
        </ButtonLink>
      </FormActions>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}
