import { useTranslation } from 'react-i18next'
import { useParams, useSearchParams } from 'react-router'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useAppointment, type Appointment } from '../calendar/api'
import { patientName, usePatient, type Patient } from '../patients/api'
import { ConsultationForm } from './ConsultationForm'
import { useCreateConsultation } from './consultationsApi'

/**
 * Records a consultation by the signed-in doctor (D-037). `?appointment=` links it to the doctor's appointment it
 * records and prefills the reason (D-038); the server checks that the link is allowed.
 */
export function NewConsultationPage() {
  const { patientId = '' } = useParams()
  const [searchParams] = useSearchParams()
  const { t } = useTranslation()
  const patient = usePatient(patientId)
  const appointmentId = searchParams.get('appointment')
  const appointment = useAppointment(appointmentId)
  const failed = patient.isError ? patient : appointment.isError ? appointment : null

  if (failed) {
    return (
      <Page>
        <ErrorState error={failed.error} onRetry={() => void failed.refetch()} retrying={failed.isFetching} />
      </Page>
    )
  }
  if (!patient.data || (appointmentId && !appointment.data)) {
    return (
      <Page>
        <LoadingRegion label={t('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Page>
    )
  }
  return <NewConsultation patient={patient.data} appointment={appointment.data ?? null} />
}

function NewConsultation({ patient, appointment }: { patient: Patient; appointment: Appointment | null }) {
  const { t } = useTranslation('medicalRecords')
  const create = useCreateConsultation(patient.id)

  return (
    <Page>
      <ContextBar title={t('consultations.newTitle', { name: patientName(patient) })} />
      <ConsultationForm
        initialReason={appointment?.reason ?? undefined}
        submitLabel={t('consultations.create')}
        successMessage={t('consultations.created')}
        cancelPath={`/patients/${patient.id}/medical-record`}
        submit={(request, callbacks) => create.mutate({ ...request, appointmentId: appointment?.id ?? null }, callbacks)}
        pending={create.isPending}
        error={create.error}
      />
    </Page>
  )
}
