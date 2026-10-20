import { describe, expect, it } from 'vitest'
import { homePathFor, navItemsFor, navLabelKey } from './navigation'

const paths = (role: Parameters<typeof navItemsFor>[0]) => navItemsFor(role).map((item) => item.path)

describe('navigation by role (ui-ux.md §38)', () => {
  it('gives the owner every module', () => {
    expect(paths('OWNER')).toEqual(['/dashboard', '/calendar', '/patients', '/team', '/schedules', '/activity', '/settings'])
  })

  it('gives doctors their personal modules but no team or settings', () => {
    expect(paths('DOCTOR')).toEqual(['/dashboard', '/calendar', '/patients', '/schedules', '/activity'])
  })

  it('gives secretaries calendar and patients only', () => {
    expect(paths('SECRETARY')).toEqual(['/calendar', '/patients'])
  })

  it('uses personal wording for doctors', () => {
    const labels = navItemsFor('DOCTOR').map((item) => navLabelKey(item, 'DOCTOR'))
    expect(labels).toEqual(['nav.myDay', 'nav.calendar', 'nav.patients', 'nav.mySchedule', 'nav.myActivity'])
  })

  it('lands secretaries on the calendar and others on the dashboard', () => {
    expect(homePathFor('SECRETARY')).toBe('/calendar')
    expect(homePathFor('DOCTOR')).toBe('/dashboard')
    expect(homePathFor('OWNER')).toBe('/dashboard')
  })
})
