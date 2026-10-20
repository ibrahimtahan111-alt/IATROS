import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { todayIn } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { FormActions, Page } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { patientName, usePatient, useUpdatePatient, type Patient } from './api'
import { PatientDetailsFields } from './PatientDetailsFields'
import styles from './PatientsPage.module.css'
import { patientDetailsOf, patientDetailsSchema, toUpdatePatientRequest, type PatientDetailsValues } from './schemas'

/** Identity and contact details. Doctors and status are managed from the profile (decisions.md D-036). */
export function EditPatientPage() {
  const { patientId = '' } = useParams()
  const { t } = useTranslation()
  const patient = usePatient(patientId)

  if (patient.isPending) {
    return (
      <Page>
        <LoadingRegion label={t('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Page>
    )
  }
  if (patient.isError) {
    return (
      <Page>
        <ErrorState error={patient.error} onRetry={() => void patient.refetch()} retrying={patient.isFetching} />
      </Page>
    )
  }
  return <EditPatientForm patient={patient.data} />
}

function EditPatientForm({ patient }: { patient: Patient }) {
  const { t } = useTranslation('patients')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const toast = useToast()
  const navigate = useNavigate()
  const errorMessage = useErrorMessage()
  const update = useUpdatePatient(patient.id)
  const today = todayIn(user.clinic.timeZone)
  const schema = useMemo(() => patientDetailsSchema(today), [today])
  const [saved, setSaved] = useState(false)
  const form = useForm<PatientDetailsValues>({
    resolver: standardSchemaResolver(schema),
    defaultValues: patientDetailsOf(patient),
    mode: 'onTouched',
  })
  const { handleSubmit, formState, setError, setFocus } = form
  const blocker = useUnsavedChangesGuard(formState.isDirty && !saved)
  const profilePath = `/patients/${patient.id}`

  useEffect(() => {
    if (saved) {
      void navigate(profilePath, { replace: true })
    }
  }, [saved, navigate, profilePath])

  const onSubmit = handleSubmit((values) =>
    update.mutate(toUpdatePatientRequest(values, patient.version), {
      onSuccess: () => {
        toast(t('edit.saved'))
        setSaved(true)
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus),
    }),
  )

  return (
    <Page>
      <ContextBar title={t('edit.title', { name: patientName(patient) })} />
      <form onSubmit={onSubmit} noValidate>
        {patient.status === 'ARCHIVED' && (
          <Alert tone="warning" className={styles.alert}>
            {t('edit.archived')}
          </Alert>
        )}
        {update.error && !hasFieldErrors(update.error) && (
          <Alert tone="danger" className={styles.alert}>
            {errorMessage(update.error)}
          </Alert>
        )}

        <PatientDetailsFields form={form} today={today} />

        <FormActions>
          <Button type="submit" variant="primary" loading={update.isPending} disabled={patient.status === 'ARCHIVED'}>
            {tCommon('actions.save')}
          </Button>
          <ButtonLink to={profilePath} variant="ghost">
            {tCommon('actions.cancel')}
          </ButtonLink>
        </FormActions>
      </form>
      <UnsavedChangesDialog blocker={blocker} />
    </Page>
  )
}
