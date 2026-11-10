import { KeyRound, MailPlus, Pencil, UserCheck, UserX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useParams, useSearchParams } from 'react-router'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { DetailList, Page, Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useSessionUser } from '../auth/session'
import { DoctorProfileSection } from '../doctors/DoctorProfileSection'
import { fullName, useStaffMember, type StaffMember } from './api'
import { EditMemberDialog } from './EditMemberDialog'
import styles from './MemberPage.module.css'
import { StaffStatusBadge } from './StaffStatusBadge'
import { useMemberActions } from './useMemberActions'

export function MemberPage() {
  const { memberId = '' } = useParams()
  const { t } = useTranslation()
  const member = useStaffMember(memberId)

  if (member.isPending) {
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
  if (member.isError) {
    return (
      <Page>
        <ErrorState error={member.error} onRetry={() => void member.refetch()} retrying={member.isFetching} />
      </Page>
    )
  }
  return <MemberDetails member={member.data} />
}

function MemberDetails({ member }: { member: StaffMember }) {
  const { t } = useTranslation('team')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const actions = useMemberActions()
  const [searchParams, setSearchParams] = useSearchParams()
  const editing = searchParams.has('edit')
  const name = fullName(member)
  const isOwner = member.role === 'OWNER'
  const notSet = <span className={styles.muted}>{t('member.notSet')}</span>

  const setEditing = (open: boolean) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (open) next.set('edit', '1')
        else next.delete('edit')
        return next
      },
      { replace: true },
    )

  return (
    <Page>
      <ContextBar
        back={{ to: '/team', label: tCommon('actions.back') }}
        title={name}
        scope={<StaffStatusBadge status={member.status} />}
        action={
          <Button icon={<Pencil aria-hidden="true" />} onClick={() => setEditing(true)}>
            {tCommon('actions.edit')}
          </Button>
        }
      />

      {member.status === 'INVITED' && (
        <Alert
          tone="info"
          className={styles.banner}
          action={
            <Button
              size="sm"
              icon={<MailPlus aria-hidden="true" />}
              loading={actions.resendPending}
              onClick={() => actions.resendInvitation(member)}
            >
              {t('actions.resendInvitation')}
            </Button>
          }
        >
          {t('member.invitedBanner', { name })}
        </Alert>
      )}
      {member.status === 'DEACTIVATED' && (
        <Alert
          tone="warning"
          className={styles.banner}
          action={
            <Button size="sm" icon={<UserCheck aria-hidden="true" />} onClick={() => actions.reactivate(member)}>
              {t('actions.reactivate')}
            </Button>
          }
        >
          {t('member.deactivatedBanner', { name })}
        </Alert>
      )}

      <Section title={t('member.account')}>
        <DetailList
          items={[
            { label: t('fields.role'), value: tCommon(`roles.${member.role}`) },
            { label: t('fields.email'), value: member.email },
            { label: t('fields.phone'), value: member.phone ?? notSet },
            {
              label: t('member.lastLogin'),
              value: member.lastLoginAt ? format.dateTime(member.lastLoginAt) : t('member.never'),
            },
            { label: t('member.addedOn'), value: format.date(member.createdAt) },
          ]}
        />
      </Section>

      {member.doctor && (
        <DoctorProfileSection doctorId={member.doctor.id} scheduleLink={`/schedules?doctor=${member.doctor.id}`} />
      )}

      {member.status === 'ACTIVE' && (
        <Section title={t('passwordReset.section')} description={t('passwordReset.consequence', { name, email: member.email })}>
          <div>
            <Button
              icon={<KeyRound aria-hidden="true" />}
              loading={actions.resetPending}
              onClick={() => actions.resetPassword(member)}
            >
              {t('passwordReset.confirm')}
            </Button>
          </div>
        </Section>
      )}

      {!isOwner && member.status !== 'DEACTIVATED' && (
        <Section title={t('actions.deactivate')} description={t('deactivate.consequence', { name })}>
          <div>
            <Button variant="danger" icon={<UserX aria-hidden="true" />} onClick={() => actions.deactivate(member)}>
              {t('actions.deactivate')}
            </Button>
          </div>
        </Section>
      )}

      <EditMemberDialog member={member} open={editing} onOpenChange={setEditing} />
      {actions.dialog}
    </Page>
  )
}
