import { Archive, ArchiveRestore, ClipboardList, Pencil } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { todayIn } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { DetailList, Page, Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useToast } from '../../shared/ui/useToast'
import { useSessionUser } from '../auth/session'
import { PatientAppointmentsSection } from '../calendar/PatientAppointmentsSection'
import { ageOn, patientName, useChangePatientStatus, usePatient, type Patient } from './api'
import { PatientDoctorsSection } from './PatientDoctorsSection'
import styles from './PatientPage.module.css'
import { PatientStatusBadge } from './PatientStatusBadge'

/** Patient profile (ui-ux.md §14): details, appointments, and doctors. Owners and doctors open the medical record from here. */
export function PatientPage() {
  const { patientId = '' } = useParams()
  const { t } = useTranslation()
  const patient = usePatient(patientId)

  if (patient.isPending) {
    return (
      <Page>
        <LoadingRegion label={t('states.loading')}>
          <div className={styles.skeleton}>
            <Skeleton width="16rem" height="var(--space-8)" />
            <Skeleton height="var(--space-16)" />
          </div>
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
  return <PatientProfile patient={patient.data} />
}

function PatientProfile({ patient }: { patient: Patient }) {
  const { t } = useTranslation('patients')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const changeStatus = useChangePatientStatus(patient.id)
  const [confirming, setConfirming] = useState<'ARCHIVED' | 'ACTIVE' | null>(null)
  const isOwner = user.role === 'OWNER'
  const archived = patient.status === 'ARCHIVED'
  const name = patientName(patient)
  const notSet = <span className={styles.muted}>{t('profile.notSet')}</span>

  const confirmStatus = () => {
    if (!confirming) return
    changeStatus.mutate(confirming, {
      onSuccess: () => toast(t(confirming === 'ARCHIVED' ? 'archive.done' : 'restore.done', { name })),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setConfirming(null),
    })
  }

  return (
    <Page>
      <ContextBar
        title={name}
        scope={<PatientStatusBadge status={patient.status} />}
        action={
          <div className={styles.sectionActions}>
            {!archived && (
              <ButtonLink to="edit" icon={<Pencil aria-hidden="true" />}>
                {tCommon('actions.edit')}
              </ButtonLink>
            )}
            {user.role !== 'SECRETARY' && (
              <ButtonLink to="medical-record" variant="primary" icon={<ClipboardList aria-hidden="true" />}>
                {t('profile.medicalRecord')}
              </ButtonLink>
            )}
          </div>
        }
      />

      {archived && (
        <Alert
          tone="warning"
          className={styles.banner}
          action={
            isOwner && (
              <Button size="sm" icon={<ArchiveRestore aria-hidden="true" />} onClick={() => setConfirming('ACTIVE')}>
                {t('restore.action')}
              </Button>
            )
          }
        >
          {t('profile.archivedBanner', { date: patient.archivedAt ? format.date(patient.archivedAt) : '' })}
        </Alert>
      )}

      <Section title={t('form.personal')}>
        <DetailList
          items={[
            {
              label: t('fields.dateOfBirth'),
              value: (
                <span className="tabular">
                  {format.localDate(patient.dateOfBirth)}{' '}
                  <span className={styles.muted}>{t('age', { count: ageOn(patient.dateOfBirth, todayIn(user.clinic.timeZone)) })}</span>
                </span>
              ),
            },
            { label: t('fields.gender'), value: patient.gender ? t(`gender.${patient.gender}`) : notSet },
            { label: t('profile.addedOn'), value: format.date(patient.createdAt) },
          ]}
        />
      </Section>

      <Section title={t('form.contact')}>
        <DetailList
          items={[
            { label: t('fields.phone'), value: patient.phone ?? notSet },
            { label: t('fields.email'), value: patient.email ?? notSet },
            { label: t('fields.address'), value: patient.address ?? notSet },
            {
              label: t('profile.emergencyContact'),
              value:
                patient.emergencyContactName || patient.emergencyContactPhone
                  ? [patient.emergencyContactName, patient.emergencyContactPhone].filter(Boolean).join(' · ')
                  : notSet,
            },
          ]}
        />
      </Section>

      <PatientAppointmentsSection patient={patient} canBook={!archived} />

      <PatientDoctorsSection patient={patient} canManage={isOwner && !archived} />

      {isOwner && !archived && (
        <Section title={t('archive.action')} description={t('archive.consequence', { name })}>
          <div>
            <Button variant="danger" icon={<Archive aria-hidden="true" />} onClick={() => setConfirming('ARCHIVED')}>
              {t('archive.action')}
            </Button>
          </div>
        </Section>
      )}

      <ConfirmDialog
        open={confirming !== null}
        onOpenChange={(open) => !open && setConfirming(null)}
        title={t(confirming === 'ACTIVE' ? 'restore.title' : 'archive.title', { name })}
        consequence={t(confirming === 'ACTIVE' ? 'restore.consequence' : 'archive.consequence', { name })}
        confirmLabel={t(confirming === 'ACTIVE' ? 'restore.action' : 'archive.action')}
        onConfirm={confirmStatus}
        destructive={confirming === 'ARCHIVED'}
        pending={changeStatus.isPending}
      />
    </Page>
  )
}
