import { Activity, CalendarDays, ClipboardClock, LayoutDashboard, Settings, SlidersHorizontal, Users, UsersRound, type LucideIcon } from 'lucide-react'
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

export function navLabelKey(item: { labelKey: string; doctorLabelKey?: string }, role: Role): string {
  return role === 'DOCTOR' && item.doctorLabelKey ? item.doctorLabelKey : item.labelKey
}

/** A rail entry. Several routes can share one entry; the switch inside the page moves between them. */
export interface RailItem {
  path: string
  /** Paths that keep this rail entry current, including nested routes. */
  match: readonly string[]
  icon: LucideIcon
  labelKey: string
  doctorLabelKey?: string
}

/**
 * What the rail shows. Overview covers Activity. Configuration covers Team, Schedules, and Settings for the owner.
 * Calendar and Patients stay separate. A doctor only has their own schedule, so that stays its own entry.
 */
export function railItemsFor(role: Role): RailItem[] {
  const items: RailItem[] = []
  if (role !== 'SECRETARY') {
    items.push({
      path: '/dashboard',
      match: ['/dashboard', '/activity'],
      icon: LayoutDashboard,
      labelKey: 'nav.overview',
      doctorLabelKey: 'nav.myDay',
    })
  }
  items.push(
    { path: '/calendar', match: ['/calendar'], icon: CalendarDays, labelKey: 'nav.calendar' },
    { path: '/patients', match: ['/patients'], icon: UsersRound, labelKey: 'nav.patients' },
  )
  if (role === 'OWNER') {
    items.push({
      path: '/team',
      match: ['/team', '/schedules', '/settings'],
      icon: SlidersHorizontal,
      labelKey: 'nav.configuration',
    })
  }
  if (role === 'DOCTOR') {
    items.push({
      path: '/schedules',
      match: ['/schedules'],
      icon: ClipboardClock,
      labelKey: 'nav.mySchedule',
    })
  }
  return items
}

export function railIsCurrent(pathname: string, item: RailItem): boolean {
  return item.match.some((path) => pathname === path || pathname.startsWith(`${path}/`))
}

/** Landing page after login (ui-ux.md §38, design-system.md §5). */
export function homePathFor(role: Role): string {
  return role === 'SECRETARY' ? '/calendar' : '/dashboard'
}

export function rolesFor(path: string): readonly Role[] {
  return NAV_ITEMS.find((item) => item.path === path)?.roles ?? []
}
