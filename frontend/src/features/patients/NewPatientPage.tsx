import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { Stethoscope, UserPlus } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { todayIn } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Field } from '../../shared/ui/Field'
import { FormActions, Page, Section } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { doctorName, useDoctorChoices, type Doctor } from '../doctors/api'
import { patientName, useCreatePatient, type Patient } from './api'
import { PatientDetailsFields } from './PatientDetailsFields'
import styles from './PatientsPage.module.css'
import { emptyNewPatient, newPatientSchema, toCreatePatientRequest, type NewPatientValues } from './schemas'

/**
 * Full-page form (ui-ux.md §13). Every patient needs a primary doctor; doctors register patients for themselves
 * (decisions.md D-036). Deactivated doctors cannot receive new patients.
 */
export function NewPatientPage() {
  const { t } = useTranslation('patients')
  const { t: tCommon } = useTranslation()
  const doctors = useDoctorChoices()
  const assignable = useMemo(() => doctors.data?.filter((doctor) => doctor.status !== 'DEACTIVATED'), [doctors.data])

  if (doctors.isPending) {
    return (
      <Page>
        <ContextBar title={t('new.title')} />
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Page>
    )
  }
  if (doctors.isError) {
    return (
      <Page>
        <ContextBar title={t('new.title')} />
        <ErrorState error={doctors.error} onRetry={() => void doctors.refetch()} retrying={doctors.isFetching} />
      </Page>
    )
  }
  if (!assignable || assignable.length === 0) {
    return (
      <Page>
        <ContextBar title={t('new.title')} />
        <NoDoctors />
      </Page>
    )
  }
  return <NewPatientForm doctors={assignable} />
}

function NewPatientForm({ doctors }: { doctors: Doctor[] }) {
  const { t } = useTranslation('patients')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const toast = useToast()
  const navigate = useNavigate()
  const create = useCreatePatient()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const today = todayIn(user.clinic.timeZone)
  const schema = useMemo(() => newPatientSchema(today), [today])
  const ownDoctor = doctors.find((doctor) => doctor.id === user.doctorId)
  const fixedDoctor = user.role === 'DOCTOR' ? ownDoctor : undefined
  const [created, setCreated] = useState<Patient | null>(null)
  const form = useForm<NewPatientValues>({
    resolver: standardSchemaResolver(schema),
    defaultValues: emptyNewPatient(ownDoctor?.id ?? (doctors.length === 1 ? doctors[0]!.id : '')),
    mode: 'onTouched',
  })
  const { control, handleSubmit, formState, setError, setFocus } = form
  const blocker = useUnsavedChangesGuard(formState.isDirty && created === null)

  useEffect(() => {
    if (created) {
      void navigate(`/patients/${created.id}`, { replace: true })
    }
  }, [created, navigate])

  const onSubmit = handleSubmit((values) =>
    create.mutate(toCreatePatientRequest(values), {
      onSuccess: (patient) => {
        toast(t('new.created', { name: patientName(patient) }))
        setCreated(patient)
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus),
    }),
  )

  return (
    <Page>
      <ContextBar title={t('new.title')} />
      <form onSubmit={onSubmit} noValidate>
        {create.error && !hasFieldErrors(create.error) && (
          <Alert tone="danger" className={styles.alert}>
            {errorMessage(create.error)}
          </Alert>
        )}

        <PatientDetailsFields form={form} today={today} />

        <Section title={t('form.clinic')} description={t('form.clinicDescription')}>
          {fixedDoctor ? (
            <p>{t('new.yourPatient', { name: doctorName(fixedDoctor) })}</p>
          ) : (
            <Field label={t('fields.primaryDoctor')} error={fieldError(formState.errors.primaryDoctorId?.message)}>
              {(props) => (
                <Controller
                  control={control}
                  name="primaryDoctorId"
                  render={({ field }) => (
                    <Select
                      {...props}
                      name={field.name}
                      value={field.value || undefined}
                      onValueChange={field.onChange}
                      placeholder={t('form.primaryDoctorPlaceholder')}
                      options={doctors.map((doctor) => ({ value: doctor.id, label: doctorName(doctor) }))}
                    />
                  )}
                />
              )}
            </Field>
          )}
        </Section>

        <FormActions>
          <Button type="submit" variant="primary" icon={<UserPlus aria-hidden="true" />} loading={create.isPending}>
            {t('new.submit')}
          </Button>
          <ButtonLink to="/patients" variant="ghost">
            {tCommon('actions.cancel')}
          </ButtonLink>
        </FormActions>
      </form>
      <UnsavedChangesDialog blocker={blocker} />
    </Page>
  )
}

function NoDoctors() {
  const { t } = useTranslation('patients')
  const user = useSessionUser()
  const canAdd = user.role === 'OWNER'
  return (
    <EmptyState
      icon={<Stethoscope />}
      title={t('new.noDoctors.title')}
      description={t(canAdd ? 'new.noDoctors.owner' : 'new.noDoctors.other')}
      action={
        canAdd ? (
          <ButtonLink to="/team/new" variant="primary" icon={<UserPlus aria-hidden="true" />}>
            {t('new.noDoctors.addDoctor')}
          </ButtonLink>
        ) : undefined
      }
    />
  )
}
