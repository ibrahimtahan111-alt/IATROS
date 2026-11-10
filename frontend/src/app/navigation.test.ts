import { describe, expect, it } from 'vitest'
import { homePathFor, navItemsFor, navLabelKey, railItemsFor, railIsCurrent } from './navigation'

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

  it('groups overview with activity, and team, schedules, and settings under configuration', () => {
    expect(railItemsFor('OWNER').map((item) => item.labelKey)).toEqual([
      'nav.overview',
      'nav.calendar',
      'nav.patients',
      'nav.configuration',
    ])
    expect(railItemsFor('DOCTOR').map((item) => navLabelKey(item, 'DOCTOR'))).toEqual([
      'nav.myDay',
      'nav.calendar',
      'nav.patients',
      'nav.mySchedule',
    ])
    expect(railItemsFor('SECRETARY').map((item) => item.path)).toEqual(['/calendar', '/patients'])
    const configuration = railItemsFor('OWNER')[3]!
    expect(railIsCurrent('/settings', configuration)).toBe(true)
    expect(railIsCurrent('/team/member', configuration)).toBe(true)
    expect(railIsCurrent('/activity', railItemsFor('OWNER')[0]!)).toBe(true)
  })

  it('lands secretaries on the calendar and others on the dashboard', () => {
    expect(homePathFor('SECRETARY')).toBe('/calendar')
    expect(homePathFor('DOCTOR')).toBe('/dashboard')
    expect(homePathFor('OWNER')).toBe('/dashboard')
  })
})
