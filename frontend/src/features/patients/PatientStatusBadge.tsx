import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '../../shared/ui/Badge'
import type { PatientStatus } from './api'

const TONES: Record<PatientStatus, BadgeTone> = { ACTIVE: 'success', ARCHIVED: 'neutral' }

export function PatientStatusBadge({ status }: { status: PatientStatus }) {
  const { t } = useTranslation('patients')
  return <Badge tone={TONES[status]}>{t(`status.${status}`)}</Badge>
}
