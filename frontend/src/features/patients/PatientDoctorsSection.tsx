import { ArrowLeftRight, UserMinus, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { Badge } from '../../shared/ui/Badge'
import { Button } from '../../shared/ui/Button'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { IconButton } from '../../shared/ui/IconButton'
import { DetailList, Section } from '../../shared/ui/Page'
import { Table, Td, Th } from '../../shared/ui/Table'
import { useToast } from '../../shared/ui/useToast'
import { doctorName, useDoctorChoices } from '../doctors/api'
import {
  patientName,
  useAddAuthorizedDoctor,
  useChangePrimaryDoctor,
  useRemoveDoctor,
  type Patient,
  type PatientDoctor,
} from './api'
import { ChooseDoctorDialog } from './ChooseDoctorDialog'
import styles from './PatientPage.module.css'

interface PatientDoctorsSectionProps {
  patient: Patient
  /** Owners manage doctor access on active patients (decisions.md D-036). */
  canManage: boolean
}

/** Which doctors may access the patient. Secretaries only see the primary doctor (D-029). */
export function PatientDoctorsSection({ patient, canManage }: PatientDoctorsSectionProps) {
  const { t } = useTranslation('patients')

  if (patient.doctors === null) {
    return (
      <Section title={t('doctors.title')}>
        <DetailList
          items={[{ label: t('fields.primaryDoctor'), value: patient.primaryDoctor ? doctorName(patient.primaryDoctor) : '—' }]}
        />
      </Section>
    )
  }
  return <ManagedDoctors patient={patient} doctors={patient.doctors} canManage={canManage} />
}

function ManagedDoctors({ patient, doctors, canManage }: { patient: Patient; doctors: PatientDoctor[]; canManage: boolean }) {
  const { t } = useTranslation('patients')
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const choices = useDoctorChoices(canManage)
  const changePrimary = useChangePrimaryDoctor(patient.id)
  const addDoctor = useAddAuthorizedDoctor(patient.id)
  const removeDoctor = useRemoveDoctor(patient.id)
  const [dialog, setDialog] = useState<'primary' | 'add' | null>(null)
  const [removing, setRemoving] = useState<PatientDoctor | null>(null)
  const name = patientName(patient)

  const linked = new Set(doctors.map((doctor) => doctor.id))
  const assignable = (choices.data ?? []).filter((doctor) => doctor.status !== 'DEACTIVATED')
  const primaryChoices = assignable.filter((doctor) => doctor.id !== patient.primaryDoctor?.id)
  const addChoices = assignable.filter((doctor) => !linked.has(doctor.id))

  const openDialog = (next: 'primary' | 'add') => {
    changePrimary.reset()
    addDoctor.reset()
    setDialog(next)
  }

  const confirmPrimary = (doctorId: string) =>
    changePrimary.mutate(doctorId, {
      onSuccess: () => {
        toast(t('doctors.primaryChanged'))
        setDialog(null)
      },
    })

  const confirmAdd = (doctorId: string) =>
    addDoctor.mutate(doctorId, {
      onSuccess: () => {
        toast(t('doctors.added'))
        setDialog(null)
      },
    })

  const confirmRemove = () => {
    if (!removing) return
    removeDoctor.mutate(removing.id, {
      onSuccess: () => toast(t('doctors.removed', { name: doctorName(removing) })),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setRemoving(null),
    })
  }

  return (
    <Section
      title={t('doctors.title')}
      description={t('doctors.description')}
      action={
        canManage && (
          <div className={styles.sectionActions}>
            <Button size="sm" icon={<ArrowLeftRight aria-hidden="true" />} onClick={() => openDialog('primary')}>
              {t('doctors.changePrimary')}
            </Button>
            <Button size="sm" icon={<UserPlus aria-hidden="true" />} onClick={() => openDialog('add')}>
              {t('doctors.add')}
            </Button>
          </div>
        )
      }
    >
      <Table caption={t('doctors.caption', { name })}>
        <thead>
          <tr>
            <Th>{t('fields.doctor')}</Th>
            <Th>{t('doctors.access')}</Th>
            {canManage && (
              <Th>
                <span className="visually-hidden">{t('doctors.actions')}</span>
              </Th>
            )}
          </tr>
        </thead>
        <tbody>
          {doctors.map((doctor) => (
            <tr key={doctor.id}>
              <Td primary>
                {doctorName(doctor)}
                {doctor.status === 'DEACTIVATED' && (
                  <>
                    {' '}
                    <Badge>{t('doctors.deactivated')}</Badge>
                  </>
                )}
              </Td>
              <Td>
                <Badge tone={doctor.relationshipType === 'PRIMARY' ? 'accent' : 'neutral'}>
                  {t(`doctors.relationship.${doctor.relationshipType}`)}
                </Badge>
              </Td>
              {canManage && (
                <Td className={styles.actionsCell}>
                  {doctor.relationshipType === 'AUTHORIZED' && (
                    <IconButton
                      label={t('doctors.remove', { name: doctorName(doctor) })}
                      icon={<UserMinus />}
                      onClick={() => setRemoving(doctor)}
                    />
                  )}
                </Td>
              )}
            </tr>
          ))}
        </tbody>
      </Table>

      {canManage && (
        <>
          <ChooseDoctorDialog
            open={dialog === 'primary'}
            onOpenChange={(open) => !open && setDialog(null)}
            title={t('doctors.changePrimaryTitle', { name })}
            description={t('doctors.changePrimaryDescription')}
            confirmLabel={t('doctors.changePrimary')}
            doctors={primaryChoices}
            onConfirm={confirmPrimary}
            pending={changePrimary.isPending}
            error={changePrimary.error}
          />
          <ChooseDoctorDialog
            open={dialog === 'add'}
            onOpenChange={(open) => !open && setDialog(null)}
            title={t('doctors.addTitle', { name })}
            description={t('doctors.addDescription')}
            confirmLabel={t('doctors.add')}
            doctors={addChoices}
            onConfirm={confirmAdd}
            pending={addDoctor.isPending}
            error={addDoctor.error}
          />
          <ConfirmDialog
            open={removing !== null}
            onOpenChange={(open) => !open && setRemoving(null)}
            title={removing ? t('doctors.removeTitle', { name: doctorName(removing) }) : ''}
            consequence={removing ? t('doctors.removeConsequence', { doctor: doctorName(removing), patient: name }) : ''}
            confirmLabel={t('doctors.removeConfirm')}
            onConfirm={confirmRemove}
            destructive
            pending={removeDoctor.isPending}
          />
        </>
      )}
    </Section>
  )
}
