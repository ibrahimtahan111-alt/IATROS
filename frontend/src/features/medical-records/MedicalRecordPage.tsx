import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { Alert } from '../../shared/ui/Alert'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useSessionUser } from '../auth/session'
import { patientName, usePatient, type Patient } from '../patients/api'
import { AllergiesSection } from './AllergiesSection'
import { useMedicalRecord, type MedicalRecord } from './api'
import { ConditionsSection } from './ConditionsSection'
import { ConsultationsSection } from './ConsultationsSection'
import { DocumentsSection } from './DocumentsSection'
import { HistorySection } from './HistorySection'
import styles from './MedicalRecord.module.css'
import { MedicationsSection } from './MedicationsSection'
import { NotesSection } from './NotesSection'

/**
 * A patient's medical record (ui-ux.md §15): structured sections, general notes, and consultations. Owners and the
 * patient's doctors only; the server refuses secretaries and unlinked doctors (D-037).
 */
export function MedicalRecordPage() {
  const { patientId = '' } = useParams()
  const { t } = useTranslation()
  const patient = usePatient(patientId)
  const record = useMedicalRecord(patientId)

  const failed = patient.isError ? patient : record.isError ? record : null
  if (failed) {
    return (
      <Page>
        <ErrorState error={failed.error} onRetry={() => void failed.refetch()} retrying={failed.isFetching} />
      </Page>
    )
  }
  if (!patient.data || !record.data) {
    return (
      <Page>
        <LoadingRegion label={t('states.loading')}>
          <div className={styles.skeleton}>
            <Skeleton width="16rem" height="var(--space-8)" />
            <Skeleton height="var(--space-16)" />
            <Skeleton height="var(--space-16)" />
          </div>
        </LoadingRegion>
      </Page>
    )
  }
  return <RecordView patient={patient.data} record={record.data} />
}

function RecordView({ patient, record }: { patient: Patient; record: MedicalRecord }) {
  const { t } = useTranslation('medicalRecords')
  const user = useSessionUser()
  const name = patientName(patient)
  const editable = patient.status === 'ACTIVE'

  return (
    <Page>
      <ContextBar
        title={t('pageTitle', { name })}
        action={
          <ButtonLink to={`/patients/${patient.id}`} variant="ghost" icon={<ArrowLeft aria-hidden="true" />}>
            {t('backToPatient', { name })}
          </ButtonLink>
        }
      />
      {!editable && (
        <Alert tone="warning" className={styles.banner}>
          {t('archivedBanner')}
        </Alert>
      )}
      <AllergiesSection patientId={patient.id} entries={record.allergies} editable={editable} />
      <ConditionsSection patientId={patient.id} entries={record.conditions} editable={editable} />
      <MedicationsSection patientId={patient.id} entries={record.medications} editable={editable} />
      <HistorySection patientId={patient.id} entries={record.history} editable={editable} />
      <NotesSection patientId={patient.id} editable={editable} />
      <ConsultationsSection patientId={patient.id} patientName={name} canCreate={editable && user.doctorId !== null} />
      <DocumentsSection patientId={patient.id} editable={editable} />
    </Page>
  )
}
