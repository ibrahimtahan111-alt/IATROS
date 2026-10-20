import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { isApiError } from '../../shared/api/ApiError'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { Field } from '../../shared/ui/Field'
import { Select } from '../../shared/ui/Select'
import { doctorName, type Doctor } from '../doctors/api'
import styles from './PatientPage.module.css'

interface ChooseDoctorDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel: string
  doctors: readonly Doctor[]
  onConfirm: (doctorId: string) => void
  pending: boolean
  error: unknown
}

/** A single doctor choice, used to change the primary doctor and to give another doctor access. */
export function ChooseDoctorDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  doctors,
  onConfirm,
  pending,
  error,
}: ChooseDoctorDialogProps) {
  const { t: tCommon } = useTranslation()
  const formId = useId()

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !pending && onOpenChange(next)}
      title={title}
      description={description}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={pending}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <ChooseDoctorForm id={formId} doctors={doctors} onConfirm={onConfirm} error={error} />
    </Dialog>
  )
}

/** Mounted with the open dialog, so every opening starts without a selection. */
function ChooseDoctorForm({
  id,
  doctors,
  onConfirm,
  error,
}: Pick<ChooseDoctorDialogProps, 'doctors' | 'onConfirm' | 'error'> & { id: string }) {
  const { t } = useTranslation('patients')
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const [doctorId, setDoctorId] = useState('')
  const [missing, setMissing] = useState(false)
  const serverFieldCode = isApiError(error) ? error.fieldErrors[0]?.code : undefined

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (doctorId === '') {
      setMissing(true)
      return
    }
    onConfirm(doctorId)
  }

  return (
    <form id={id} className={styles.dialogBody} onSubmit={submit} noValidate>
      {Boolean(error) && !hasFieldErrors(error) && <Alert tone="danger">{errorMessage(error)}</Alert>}
      <Field label={t('fields.doctor')} error={fieldError(missing ? 'REQUIRED' : serverFieldCode)}>
        {(props) => (
          <Select
            {...props}
            value={doctorId || undefined}
            onValueChange={(value) => {
              setDoctorId(value)
              setMissing(false)
            }}
            placeholder={t('form.primaryDoctorPlaceholder')}
            options={doctors.map((doctor) => ({ value: doctor.id, label: doctorName(doctor) }))}
          />
        )}
      </Field>
    </form>
  )
}
