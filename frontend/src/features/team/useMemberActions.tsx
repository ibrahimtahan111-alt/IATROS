import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { useToast } from '../../shared/ui/useToast'
import { fullName, useChangeStaffStatus, useRequestPasswordReset, useResendInvitation, type StaffMember } from './api'

type StatusChange = { member: StaffMember; status: 'ACTIVE' | 'DEACTIVATED' }

/**
 * Deactivate / reactivate (with confirmation) and resend invitation, shared by the team list and the member page.
 * Render `dialog` once; outcomes are reported with toasts.
 */
export function useMemberActions() {
  const { t } = useTranslation('team')
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const changeStatus = useChangeStaffStatus()
  const resend = useResendInvitation()
  const resetPassword = useRequestPasswordReset()
  const [change, setChange] = useState<StatusChange | null>(null)
  const [resetTarget, setResetTarget] = useState<StaffMember | null>(null)

  const confirmChange = () => {
    if (!change) return
    const name = fullName(change.member)
    changeStatus.mutate(
      { id: change.member.id, status: change.status },
      {
        onSuccess: () => toast(t(change.status === 'DEACTIVATED' ? 'deactivate.done' : 'reactivate.done', { name })),
        onError: (error) => toast(errorMessage(error), 'danger'),
        onSettled: () => setChange(null),
      },
    )
  }

  const resendInvitation = (member: StaffMember) =>
    resend.mutate(member.id, {
      onSuccess: () => toast(t('invitation.sent', { email: member.email })),
      onError: (error) => toast(errorMessage(error), 'danger'),
    })

  const confirmReset = () => {
    if (!resetTarget) return
    const member = resetTarget
    resetPassword.mutate(member.id, {
      onSuccess: () => toast(t('passwordReset.done', { email: member.email })),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setResetTarget(null),
    })
  }

  const name = change ? fullName(change.member) : ''
  const deactivating = change?.status === 'DEACTIVATED'

  const resetName = resetTarget ? fullName(resetTarget) : ''
  const dialog = (
    <>
      <ConfirmDialog
        open={change !== null}
        onOpenChange={(open) => !open && setChange(null)}
        title={t(deactivating ? 'deactivate.title' : 'reactivate.title', { name })}
        consequence={t(deactivating ? 'deactivate.consequence' : 'reactivate.consequence', { name })}
        confirmLabel={t(deactivating ? 'deactivate.confirm' : 'reactivate.confirm')}
        onConfirm={confirmChange}
        destructive={deactivating}
        pending={changeStatus.isPending}
      />
      <ConfirmDialog
        open={resetTarget !== null}
        onOpenChange={(open) => !open && setResetTarget(null)}
        title={t('passwordReset.title', { name: resetName })}
        consequence={t('passwordReset.consequence', { name: resetName, email: resetTarget?.email ?? '' })}
        confirmLabel={t('passwordReset.confirm')}
        onConfirm={confirmReset}
        pending={resetPassword.isPending}
      />
    </>
  )

  return {
    deactivate: (member: StaffMember) => setChange({ member, status: 'DEACTIVATED' }),
    reactivate: (member: StaffMember) => setChange({ member, status: 'ACTIVE' }),
    resendInvitation,
    resendPending: resend.isPending,
    resetPassword: (member: StaffMember) => setResetTarget(member),
    resetPending: resetPassword.isPending,
    dialog,
  }
}
