import { Ellipsis, Eye, KeyRound, MailPlus, Pencil, UserCheck, UserX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import iconButtonStyles from '../../shared/ui/IconButton.module.css'
import { Menu, MenuItem, MenuSeparator } from '../../shared/ui/Menu'
import { fullName, type StaffMember } from './api'
import type { useMemberActions } from './useMemberActions'

interface MemberActionsMenuProps {
  member: StaffMember
  actions: ReturnType<typeof useMemberActions>
}

/** Row overflow menu (design-system.md §11.3). The owner's own row has no status actions (D-034). */
export function MemberActionsMenu({ member, actions }: MemberActionsMenuProps) {
  const { t } = useTranslation('team')
  const navigate = useNavigate()
  const isOwner = member.role === 'OWNER'

  return (
    <Menu
      trigger={
        <button type="button" className={iconButtonStyles.iconButton} aria-label={t('actions.menu', { name: fullName(member) })}>
          <Ellipsis aria-hidden="true" />
        </button>
      }
    >
      <MenuItem icon={<Eye />} onSelect={() => void navigate(member.id)}>
        {t('actions.view')}
      </MenuItem>
      <MenuItem icon={<Pencil />} onSelect={() => void navigate(`${member.id}?edit=1`)}>
        {t('actions.edit')}
      </MenuItem>
      {member.status === 'INVITED' && (
        <MenuItem icon={<MailPlus />} onSelect={() => actions.resendInvitation(member)}>
          {t('actions.resendInvitation')}
        </MenuItem>
      )}
      {member.status === 'ACTIVE' && (
        <MenuItem icon={<KeyRound />} onSelect={() => actions.resetPassword(member)}>
          {t('passwordReset.confirm')}
        </MenuItem>
      )}
      {!isOwner && member.status !== 'DEACTIVATED' && (
        <>
          <MenuSeparator />
          <MenuItem icon={<UserX />} destructive onSelect={() => actions.deactivate(member)}>
            {t('actions.deactivate')}
          </MenuItem>
        </>
      )}
      {!isOwner && member.status === 'DEACTIVATED' && (
        <MenuItem icon={<UserCheck />} onSelect={() => actions.reactivate(member)}>
          {t('actions.reactivate')}
        </MenuItem>
      )}
    </Menu>
  )
}
