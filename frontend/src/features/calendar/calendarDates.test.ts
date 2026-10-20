import { describe, expect, it } from 'vitest'
import { layoutLanes, mergeRanges, shiftDate, spanOn, startOfWeek, viewRange } from './calendarDates'

describe('calendar dates', () => {
  it('starts weeks on the clinic’s week start day', () => {
    expect(startOfWeek('2026-10-14', 1)).toBe('2026-10-12')
    expect(startOfWeek('2026-10-14', 7)).toBe('2026-10-11')
    expect(startOfWeek('2026-10-12', 1)).toBe('2026-10-12')
  })

  it('covers six whole weeks in the month view, the API’s maximum range', () => {
    expect(viewRange('month', '2026-10-20', 1)).toEqual({ from: '2026-09-28', to: '2026-11-08' })
    expect(viewRange('week', '2026-10-14', 1)).toEqual({ from: '2026-10-12', to: '2026-10-18' })
    expect(viewRange('day', '2026-10-14', 1)).toEqual({ from: '2026-10-14', to: '2026-10-14' })
  })

  it('moves by one view step, keeping month steps on valid dates', () => {
    expect(shiftDate('day', '2026-10-31', 1)).toBe('2026-11-01')
    expect(shiftDate('week', '2026-10-14', -1)).toBe('2026-10-07')
    expect(shiftDate('month', '2026-01-31', 1)).toBe('2026-02-01')
  })

  it('places an appointment on its clinic-local day, split at midnight', () => {
    const lateEvening = { startAt: '2026-10-12T20:30:00Z', endAt: '2026-10-12T21:30:00Z' }
    // Europe/Athens is UTC+3 in October: 23:30–00:30 local.
    expect(spanOn(lateEvening, '2026-10-12', 'Europe/Athens')).toEqual({ start: 23 * 60 + 30, end: 24 * 60 })
    expect(spanOn(lateEvening, '2026-10-13', 'Europe/Athens')).toEqual({ start: 0, end: 30 })
    expect(spanOn(lateEvening, '2026-10-14', 'Europe/Athens')).toBeNull()
  })

  it('lays overlapping appointments side by side and the others full width', () => {
    const placed = layoutLanes([
      { item: 'a', start: 540, end: 600 },
      { item: 'b', start: 570, end: 630 },
      { item: 'c', start: 660, end: 690 },
    ])
    expect(placed.map(({ item, lane, lanes }) => [item, lane, lanes])).toEqual([
      ['a', 0, 2],
      ['b', 1, 2],
      ['c', 0, 1],
    ])
  })

  it('merges overlapping and touching working ranges', () => {
    expect(
      mergeRanges([
        { start: '13:00', end: '17:00' },
        { start: '09:00', end: '12:00' },
        { start: '12:00', end: '13:30' },
      ]),
    ).toEqual([{ start: '09:00', end: '17:00' }])
  })
})
