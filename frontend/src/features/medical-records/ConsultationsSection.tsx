import { Stethoscope } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Section } from '../../shared/ui/Page'
import { Pagination } from '../../shared/ui/Pagination'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { Table, Td, Th } from '../../shared/ui/Table'
import { useSessionUser } from '../auth/session'
import { personName } from './api'
import { CONSULTATIONS_PAGE_SIZE, useConsultations } from './consultationsApi'
import styles from './MedicalRecord.module.css'

interface ConsultationsSectionProps {
  patientId: string
  patientName: string
  /** Doctors record consultations as themselves, on active patients (D-037). */
  canCreate: boolean
}

/** The patient's consultations, newest first. Every clinician with access sees all of them. */
export function ConsultationsSection({ patientId, patientName, canCreate }: ConsultationsSectionProps) {
  const { t } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const [page, setPage] = useState(0)
  const list = useConsultations(patientId, page)
  const basePath = `/patients/${patientId}/consultations`

  return (
    <Section
      title={t('consultations.title')}
      action={
        canCreate && (
          <ButtonLink to={`${basePath}/new`} size="sm" icon={<Stethoscope aria-hidden="true" />}>
            {t('consultations.new')}
          </ButtonLink>
        )
      }
    >
      {list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} retrying={list.isFetching} />
      ) : !list.data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      ) : list.data.totalElements === 0 ? (
        <p className={styles.empty}>{t('consultations.empty')}</p>
      ) : (
        <>
          <Table caption={t('consultations.caption', { name: patientName })} aria-busy={list.isFetching || undefined}>
            <thead>
              <tr>
                <Th>{t('consultations.columns.date')}</Th>
                <Th>{t('consultations.columns.doctor')}</Th>
                <Th>{t('consultations.columns.reason')}</Th>
                <Th>{t('consultations.columns.diagnosis')}</Th>
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((consultation) => (
                <tr key={consultation.id}>
                  <Td primary className="tabular">
                    <Link to={`${basePath}/${consultation.id}`}>{format.dateTime(consultation.consultedAt)}</Link>
                  </Td>
                  <Td>{personName(consultation.doctor)}</Td>
                  <Td>{consultation.reason}</Td>
                  <Td>{consultation.diagnosis ?? <span className={styles.muted}>—</span>}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
          {list.data.totalElements > CONSULTATIONS_PAGE_SIZE && (
            <Pagination page={list.data.page} size={list.data.size} totalElements={list.data.totalElements} onPageChange={setPage} />
          )}
        </>
      )}
    </Section>
  )
}
