import { ArrowLeft, Pencil } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Alert } from '../../shared/ui/Alert'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { DetailList, Page, Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useSessionUser } from '../auth/session'
import { patientName, usePatient } from '../patients/api'
import { personName } from './api'
import { useConsultation, type Consultation } from './consultationsApi'
import { DocumentsSection } from './DocumentsSection'
import styles from './MedicalRecord.module.css'

/** One consultation in full (ui-ux.md §16). Only its doctor sees the edit action; the server enforces it (D-037). */
export function ConsultationPage() {
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
  return <ConsultationDetails consultation={consultation.data} />
}

function ConsultationDetails({ consultation }: { consultation: Consultation }) {
  const { t } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const patientId = consultation.patientId
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const patient = usePatient(patientId)
  const archived = patient.data?.status === 'ARCHIVED'
  const doctor = personName(consultation.doctor)
  const notRecorded = <span className={styles.muted}>{t('consultations.notRecorded')}</span>
  const text = (value: string | null): ReactNode => (value ? <p className={styles.clinicalText}>{value}</p> : notRecorded)

  return (
    <Page>
      <ContextBar
        title={t('consultations.detailTitle', { date: format.dateTime(consultation.consultedAt) })}
        scope={patient.data && patientName(patient.data)}
        action={
          consultation.editable && !archived ? (
            <ButtonLink to="edit" icon={<Pencil aria-hidden="true" />}>
              {tCommon('actions.edit')}
            </ButtonLink>
          ) : (
            <ButtonLink to={`/patients/${patientId}/medical-record`} variant="ghost" icon={<ArrowLeft aria-hidden="true" />}>
              {t('title')}
            </ButtonLink>
          )
        }
      />
      {!consultation.editable && (
        <Alert className={styles.banner}>{t('consultations.readOnly', { doctor })}</Alert>
      )}
      <Section title={t('consultations.title')}>
        <DetailList
          items={[
            { label: t('consultations.fields.doctor'), value: doctor },
            { label: t('consultations.fields.consultedAt'), value: format.dateTime(consultation.consultedAt) },
            { label: t('consultations.fields.reason'), value: consultation.reason },
            {
              label: t('consultations.fields.followUpOn'),
              value: consultation.followUpOn ? format.localDate(consultation.followUpOn) : notRecorded,
            },
          ]}
        />
      </Section>
      <Section title={t('consultations.fields.clinicalNotes')}>{text(consultation.clinicalNotes)}</Section>
      <Section title={t('consultations.fields.diagnosis')}>{text(consultation.diagnosis)}</Section>
      <Section title={t('consultations.fields.treatmentNotes')}>{text(consultation.treatmentNotes)}</Section>
      <Section title={t('consultations.fields.followUpNotes')}>{text(consultation.followUpNotes)}</Section>
      <DocumentsSection patientId={patientId} consultationId={consultation.id} editable={patient.data?.status === 'ACTIVE'} />
      <p className={styles.muted}>{t('consultations.lastChanged', { date: format.dateTime(consultation.updatedAt) })}</p>
    </Page>
  )
}
