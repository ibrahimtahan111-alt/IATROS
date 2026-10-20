import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { Alert } from '../../shared/ui/Alert'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { patientName, usePatient } from '../patients/api'
import { personName } from './api'
import { ConsultationForm } from './ConsultationForm'
import { useConsultation, useUpdateConsultation, type Consultation } from './consultationsApi'
import styles from './MedicalRecord.module.css'

/** Edits a consultation; only its doctor may, and never on an archived patient (D-037). */
export function EditConsultationPage() {
  const { consultationId = '' } = useParams()
  const { t } = useTranslation()
  const consultation = useConsultation(consultationId)

  if (consultation.isError) {
    return (
      <Page>
        <ErrorState error={consultation.error} onRetry={() => void consultation.refetch()} retrying={consultation.isFetching} />
      </Page>
    )
  }
  if (!consultation.data) {
    return (
      <Page>
        <LoadingRegion label={t('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Page>
    )
  }
  return <EditConsultation consultation={consultation.data} />
}

function EditConsultation({ consultation }: { consultation: Consultation }) {
  const { t } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const patient = usePatient(consultation.patientId)
  const update = useUpdateConsultation(consultation.id)
  const name = patient.data ? patientName(patient.data) : ''
  const detailPath = `/patients/${consultation.patientId}/consultations/${consultation.id}`

  return (
    <Page>
      <ContextBar title={t('consultations.editTitle', { name })} />
      {!consultation.editable && (
        <Alert tone="warning" className={styles.alert}>
          {t('consultations.readOnly', { doctor: personName(consultation.doctor) })}
        </Alert>
      )}
      <ConsultationForm
        consultation={consultation}
        submitLabel={tCommon('actions.save')}
        successMessage={t('consultations.saved')}
        cancelPath={detailPath}
        submit={(request, callbacks) => update.mutate({ ...request, version: consultation.version }, callbacks)}
        pending={update.isPending}
        error={update.error}
      />
    </Page>
  )
}
