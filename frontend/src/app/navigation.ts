import { Activity, CalendarDays, ClipboardClock, LayoutDashboard, Settings, Users, UsersRound, type LucideIcon } from 'lucide-react'
import type { Role } from '../features/auth/api'

export interface NavItem {
  path: string
  icon: LucideIcon
  /** Translation key; doctors see personal wording for their own modules (ui-ux.md §38). */
  labelKey: string
  /** Roles allowed to open the route. The backend still authorizes every request. */
  roles: readonly Role[]
  doctorLabelKey?: string
}

/**
 * Primary navigation by role (ui-ux.md §38). The same list drives route guards, so a module hidden from the rail
 * cannot be reached through the URL either. The backend still authorizes every request independently.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: '/dashboard', icon: LayoutDashboard, labelKey: 'nav.overview', doctorLabelKey: 'nav.myDay', roles: ['OWNER', 'DOCTOR'] },
  { path: '/calendar', icon: CalendarDays, labelKey: 'nav.calendar', roles: ['OWNER', 'DOCTOR', 'SECRETARY'] },
  { path: '/patients', icon: UsersRound, labelKey: 'nav.patients', roles: ['OWNER', 'DOCTOR', 'SECRETARY'] },
  { path: '/team', icon: Users, labelKey: 'nav.team', roles: ['OWNER'] },
  { path: '/schedules', icon: ClipboardClock, labelKey: 'nav.schedules', doctorLabelKey: 'nav.mySchedule', roles: ['OWNER', 'DOCTOR'] },
  { path: '/activity', icon: Activity, labelKey: 'nav.activity', doctorLabelKey: 'nav.myActivity', roles: ['OWNER', 'DOCTOR'] },
  { path: '/settings', icon: Settings, labelKey: 'nav.settings', roles: ['OWNER'] },
]

export function navItemsFor(role: Role): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.includes(role))
}

export function navLabelKey(item: NavItem, role: Role): string {
  return role === 'DOCTOR' && item.doctorLabelKey ? item.doctorLabelKey : item.labelKey
}

/** Landing page after login (ui-ux.md §38, design-system.md §5). */
export function homePathFor(role: Role): string {
  return role === 'SECRETARY' ? '/calendar' : '/dashboard'
}

export function rolesFor(path: string): readonly Role[] {
  return NAV_ITEMS.find((item) => item.path === path)?.roles ?? []
}
