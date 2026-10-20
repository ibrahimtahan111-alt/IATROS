import type { FormEventHandler, ReactNode } from 'react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import styles from './MedicalRecord.module.css'

interface EntryDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  onSubmit: FormEventHandler<HTMLFormElement>
  pending: boolean
  error: unknown
  children: ReactNode
}

/** Dialog chrome shared by the add/edit forms of the record: cancel, save, and errors not tied to a field. */
export function EntryDialog({ open, onOpenChange, title, onSubmit, pending, error, children }: EntryDialogProps) {
  const { t } = useTranslation('medicalRecords')
  const { t: tCommon } = useTranslation()
  const errorMessage = useErrorMessage()
  const formId = useId()

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !pending && onOpenChange(next)}
      title={title}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={pending}>
            {t('actions.saveEntry')}
          </Button>
        </>
      }
    >
      <form id={formId} className={styles.dialogForm} onSubmit={onSubmit} noValidate>
        {Boolean(error) && !hasFieldErrors(error) && <Alert tone="danger">{errorMessage(error)}</Alert>}
        {children}
      </form>
    </Dialog>
  )
}
