import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '../../shared/ui/Badge'
import type { StaffStatus } from './api'

const TONES: Record<StaffStatus, BadgeTone> = { INVITED: 'info', ACTIVE: 'success', DEACTIVATED: 'neutral' }

export function StaffStatusBadge({ status }: { status: StaffStatus }) {
  const { t } = useTranslation('team')
  return <Badge tone={TONES[status]}>{t(`status.${status}`)}</Badge>
}
