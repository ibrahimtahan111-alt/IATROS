import { useSessionUser } from '../auth/session'
import { DoctorDashboardView } from './DoctorDashboardView'
import { OwnerDashboardView } from './OwnerDashboardView'

/** The role's home (ui-ux.md §7–8): owners get the clinic overview, doctors their own day. */
export function DashboardPage() {
  const user = useSessionUser()
  return user.role === 'OWNER' ? <OwnerDashboardView /> : <DoctorDashboardView />
}
