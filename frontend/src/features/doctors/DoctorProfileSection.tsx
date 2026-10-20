import { CalendarClock, Pencil } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ErrorState } from '../../shared/ui/ErrorState'
import { DetailList, Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useDoctor } from './api'
import { EditDoctorProfileDialog } from './EditDoctorProfileDialog'
import styles from './DoctorProfileSection.module.css'
import { useSpecialtyLabel } from './specialties'

interface DoctorProfileSectionProps {
  doctorId: string
  /** Where "Working hours" leads; omitted when the page already is the schedule. */
  scheduleLink?: string
}

/** Professional details with an edit action. Requires the `doctors` and `specialties` namespaces. */
export function DoctorProfileSection({ doctorId, scheduleLink }: DoctorProfileSectionProps) {
  const { t } = useTranslation('doctors')
  const { t: tCommon } = useTranslation()
  const doctor = useDoctor(doctorId)
  const specialtyLabel = useSpecialtyLabel()
  const [editing, setEditing] = useState(false)
  const notSet = <span className={styles.muted}>{t('profile.notSet')}</span>

  return (
    <Section
      title={t('profile.title')}
      action={
        doctor.data && (
          <Button size="sm" icon={<Pencil aria-hidden="true" />} onClick={() => setEditing(true)}>
            {tCommon('actions.edit')}
          </Button>
        )
      }
    >
      {doctor.isPending && (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-12)" />
        </LoadingRegion>
      )}
      {doctor.isError && (
        <ErrorState error={doctor.error} onRetry={() => void doctor.refetch()} retrying={doctor.isFetching} />
      )}
      {doctor.data && (
        <>
          <DetailList
            items={[
              { label: t('fields.primarySpecialty'), value: specialtyLabel(doctor.data.primarySpecialty) },
              { label: t('fields.additionalSpecialty'), value: doctor.data.additionalSpecialty ?? notSet },
              { label: t('fields.registrationNumber'), value: doctor.data.registrationNumber ?? notSet },
            ]}
          />
          {scheduleLink && (
            <div>
              <ButtonLink to={scheduleLink} size="sm" icon={<CalendarClock aria-hidden="true" />}>
                {t('profile.manageSchedule')}
              </ButtonLink>
            </div>
          )}
          <EditDoctorProfileDialog doctor={doctor.data} open={editing} onOpenChange={setEditing} />
        </>
      )}
    </Section>
  )
}
