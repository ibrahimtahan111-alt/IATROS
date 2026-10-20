import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { useEffect, useId } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Dialog } from '../../shared/ui/Dialog'
import { useToast } from '../../shared/ui/useToast'
import { fullName, useUpdateStaffMember, type StaffMember } from './api'
import { ContactFields } from './ContactFields'
import { editMemberSchema, toUpdateRequest, type EditMemberValues } from './schemas'
import styles from './EditMemberDialog.module.css'

interface EditMemberDialogProps {
  member: StaffMember
  open: boolean
  onOpenChange: (open: boolean) => void
}

function valuesOf(member: StaffMember): EditMemberValues {
  return { firstName: member.firstName, lastName: member.lastName, email: member.email, phone: member.phone ?? '' }
}

/** Contact details only; role is fixed after creation and doctor profiles have their own dialog (D-034). */
export function EditMemberDialog({ member, open, onOpenChange }: EditMemberDialogProps) {
  const { t } = useTranslation('team')
  const { t: tCommon } = useTranslation()
  const formId = useId()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const update = useUpdateStaffMember(member.id)
  const form = useForm<EditMemberValues>({
    resolver: standardSchemaResolver(editMemberSchema),
    defaultValues: valuesOf(member),
    mode: 'onTouched',
  })
  const { reset, setError, setFocus, handleSubmit } = form

  useEffect(() => {
    if (open) {
      reset(valuesOf(member))
      update.reset()
    }
    // Reset only when the dialog opens, not when the member refreshes underneath an open form.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit((values) =>
    update.mutate(toUpdateRequest(values, member.version), {
      onSuccess: () => {
        toast(t('edit.saved'))
        onOpenChange(false)
      },
      onError: (error) => applyFieldErrors(error, setError, setFocus),
    }),
  )

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !update.isPending && onOpenChange(next)}
      title={t('edit.title', { name: fullName(member) })}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={update.isPending}>
            {tCommon('actions.cancel')}
          </Button>
          <Button type="submit" form={formId} variant="primary" loading={update.isPending}>
            {tCommon('actions.save')}
          </Button>
        </>
      }
    >
      <form id={formId} className={styles.form} onSubmit={onSubmit} noValidate>
        {update.error && !hasFieldErrors(update.error) && <Alert tone="danger">{errorMessage(update.error)}</Alert>}
        <ContactFields form={form} emailEditable={member.status === 'INVITED'} />
      </form>
    </Dialog>
  )
}
