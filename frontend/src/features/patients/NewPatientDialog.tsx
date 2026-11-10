import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { Maximize2, Minimize2, Stethoscope, UserPlus } from 'lucide-react'
import { useEffect, useId, useMemo, useState, type ReactNode } from 'react'
import { flushSync } from 'react-dom'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { isApiError } from '../../shared/api/ApiError'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { cx } from '../../shared/lib/cx'
import { todayIn } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { Dialog } from '../../shared/ui/Dialog'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Field } from '../../shared/ui/Field'
import { IconButton } from '../../shared/ui/IconButton'
import { Select } from '../../shared/ui/Select'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { doctorName, useDoctorChoices, type Doctor } from '../doctors/api'
import { patientName, useCreatePatient, type Patient } from './api'
import { PatientDetailField } from './PatientDetailsFields'
import styles from './PatientsPage.module.css'
import {
  emptyNewPatient,
  newPatientSchema,
  toCreatePatientRequest,
  type NewPatientValues,
  type PatientDetailsValues,
} from './schemas'

/** Shown only once the dialog is expanded; everything else fits the compact dialog. */
const EXPANDED_ONLY_FIELDS: readonly string[] = ['gender', 'email', 'address', 'emergencyContactName', 'emergencyContactPhone']

/**
 * Add patient (ui-ux.md §13): a dialog over the patient list at `/patients/new`. It opens with the essentials and
 * expands to every detail. Every patient needs a primary doctor; doctors register patients for themselves
 * (decisions.md D-036). Deactivated doctors cannot receive new patients.
 */
export function NewPatientDialog() {
  const { t } = useTranslation('patients')
  const { t: tCommon } = useTranslation()
  const navigate = useNavigate()
  const { search } = useLocation()
  const formId = useId()
  const create = useCreatePatient()
  const doctors = useDoctorChoices()
  const assignable = useMemo(() => doctors.data?.filter((doctor) => doctor.status !== 'DEACTIVATED'), [doctors.data])
  const [expanded, setExpanded] = useState(false)
  const close = () => void navigate({ pathname: '/patients', search })
  const ready = assignable !== undefined && assignable.length > 0

  let body: ReactNode
  if (doctors.isPending) {
    body = (
      <LoadingRegion label={tCommon('states.loading')}>
        <Skeleton height="var(--space-16)" />
      </LoadingRegion>
    )
  } else if (doctors.isError) {
    body = <ErrorState error={doctors.error} onRetry={() => void doctors.refetch()} retrying={doctors.isFetching} />
  } else if (!ready) {
    body = <NoDoctors />
  } else {
    body = (
      <NewPatientForm id={formId} doctors={assignable} expanded={expanded} onExpand={() => setExpanded(true)} create={create} />
    )
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => !open && !create.isPending && close()}
      title={t('new.title')}
      size={expanded ? 'wide' : 'default'}
      headerActions={
        ready && (
          <IconButton
            label={expanded ? t('new.showFewerFields') : t('new.showAllFields')}
            icon={expanded ? <Minimize2 /> : <Maximize2 />}
            aria-expanded={expanded}
            aria-controls={formId}
            onClick={() => setExpanded((current) => !current)}
          />
        )
      }
      footer={
        ready && (
          <>
            <Button variant="ghost" onClick={close} disabled={create.isPending}>
              {tCommon('actions.cancel')}
            </Button>
            <Button type="submit" form={formId} variant="primary" icon={<UserPlus aria-hidden="true" />} loading={create.isPending}>
              {t('new.submit')}
            </Button>
          </>
        )
      }
    >
      {body}
    </Dialog>
  )
}

interface NewPatientFormProps {
  id: string
  doctors: Doctor[]
  expanded: boolean
  onExpand: () => void
  create: ReturnType<typeof useCreatePatient>
}

function NewPatientForm({ id, doctors, expanded, onExpand, create }: NewPatientFormProps) {
  const { t } = useTranslation('patients')
  const user = useSessionUser()
  const toast = useToast()
  const navigate = useNavigate()
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

  // Runs before the dialog's own initial focus, which keeps focus that is already inside it.
  useEffect(() => setFocus('firstName'), [setFocus])

  useEffect(() => {
    if (created) {
      void navigate(`/patients/${created.id}`, { replace: true })
    }
  }, [created, navigate])

  /** Invalid fields must be on screen before focus can move to the first one. */
  const revealFields = (fields: readonly string[]) => {
    if (!expanded && fields.some((field) => EXPANDED_ONLY_FIELDS.includes(field))) {
      flushSync(onExpand)
    }
  }

  const onSubmit = handleSubmit(
    (values) =>
      create.mutate(toCreatePatientRequest(values), {
        onSuccess: (patient) => {
          toast(t('new.created', { name: patientName(patient) }))
          setCreated(patient)
        },
        onError: (error) => {
          if (isApiError(error)) {
            revealFields(error.fieldErrors.map(({ field }) => field))
          }
          applyFieldErrors(error, setError, setFocus)
        },
      }),
    (errors) => revealFields(Object.keys(errors)),
  )

  const field = (name: keyof PatientDetailsValues) => <PatientDetailField form={form} today={today} name={name} />

  return (
    <>
      <form id={id} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
      {create.error && !hasFieldErrors(create.error) && <Alert tone="danger">{errorMessage(create.error)}</Alert>}

      <div className={styles.dialogGrid}>
        {field('firstName')}
        {field('lastName')}
        <div className={cx(!expanded && styles.fullRow)}>{field('dateOfBirth')}</div>
        {expanded && field('gender')}
        <div className={cx(!expanded && styles.fullRow)}>{field('phone')}</div>
        {expanded && (
          <>
            {field('email')}
            <div className={styles.fullRow}>{field('address')}</div>
            {field('emergencyContactName')}
            {field('emergencyContactPhone')}
          </>
        )}
        <div className={styles.fullRow}>
          {fixedDoctor ? (
            <p className={styles.muted}>{t('new.yourPatient', { name: doctorName(fixedDoctor) })}</p>
          ) : (
            <Field
              label={t('fields.primaryDoctor')}
              helper={t('form.clinicDescription')}
              error={fieldError(formState.errors.primaryDoctorId?.message)}
            >
              {(props) => (
                <Controller
                  control={control}
                  name="primaryDoctorId"
                  render={({ field: doctorField }) => (
                    <Select
                      {...props}
                      name={doctorField.name}
                      value={doctorField.value || undefined}
                      onValueChange={doctorField.onChange}
                      placeholder={t('form.primaryDoctorPlaceholder')}
                      options={doctors.map((doctor) => ({ value: doctor.id, label: doctorName(doctor) }))}
                    />
                  )}
                />
              )}
            </Field>
          )}
        </div>
      </div>
      </form>
      <UnsavedChangesDialog blocker={blocker} />
    </>
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
