import { addDays, dateValue, localDateTimeOf, minutesOf } from '../../shared/lib/localDates'

/**
 * Calendar math on clinic-local "YYYY-MM-DD" dates (D-004). Dates are handled as UTC values so the device's time
 * zone never shifts them; instants are converted with the clinic's zone.
 */

export const CALENDAR_VIEWS = ['day', 'week', 'month'] as const
export type CalendarView = (typeof CALENDAR_VIEWS)[number]

export const MINUTES_PER_DAY = 24 * 60

/** ISO day of week: 1 = Monday … 7 = Sunday. */
export function isoWeekday(date: string): number {
  return ((dateValue(date).getUTCDay() + 6) % 7) + 1
}

export function startOfWeek(date: string, weekStartDay: number): string {
  return addDays(date, -((isoWeekday(date) - weekStartDay + 7) % 7))
}

export function addMonths(date: string, months: number): string {
  const value = dateValue(date)
  value.setUTCDate(1)
  value.setUTCMonth(value.getUTCMonth() + months)
  return value.toISOString().slice(0, 10)
}

export function datesBetween(from: string, to: string): string[] {
  const dates: string[] = []
  for (let date = from; date <= to; date = addDays(date, 1)) {
    dates.push(date)
  }
  return dates
}

/** The dates a view shows. The month view always spans six whole weeks (42 days, the API's maximum range). */
export function viewRange(view: CalendarView, date: string, weekStartDay: number): { from: string; to: string } {
  if (view === 'day') {
    return { from: date, to: date }
  }
  if (view === 'week') {
    const from = startOfWeek(date, weekStartDay)
    return { from, to: addDays(from, 6) }
  }
  const from = startOfWeek(`${date.slice(0, 8)}01`, weekStartDay)
  return { from, to: addDays(from, 41) }
}

/** The date one view step before or after. */
export function shiftDate(view: CalendarView, date: string, step: -1 | 1): string {
  if (view === 'day') return addDays(date, step)
  if (view === 'week') return addDays(date, 7 * step)
  return addMonths(date, step)
}

/** An instant as its clinic-local date and minute of day. */
export function localPoint(instant: string, timeZone: string): { date: string; minutes: number } {
  const local = localDateTimeOf(instant, timeZone)
  return { date: local.slice(0, 10), minutes: minutesOf(local.slice(11)) }
}

export interface TimedItem {
  startAt: string
  endAt: string
}

/** The part of an item that falls on a date, in minutes of that day, or null if it does not touch the date. */
export function spanOn(item: TimedItem, date: string, timeZone: string): { start: number; end: number } | null {
  const start = localPoint(item.startAt, timeZone)
  const end = localPoint(item.endAt, timeZone)
  if (start.date > date || end.date < date || (end.date === date && end.minutes === 0 && start.date < date)) {
    return null
  }
  return {
    start: start.date < date ? 0 : start.minutes,
    end: end.date > date ? MINUTES_PER_DAY : end.minutes,
  }
}

/** "HH:mm" ranges joined where they overlap or touch, in start order. */
export function mergeRanges<R extends { start: string; end: string }>(ranges: readonly R[]): { start: string; end: string }[] {
  const merged: { start: string; end: string }[] = []
  for (const range of [...ranges].sort((a, b) => minutesOf(a.start) - minutesOf(b.start))) {
    const last = merged.at(-1)
    if (last && minutesOf(range.start) <= minutesOf(last.end)) {
      if (minutesOf(range.end) > minutesOf(last.end)) last.end = range.end
    } else {
      merged.push({ start: range.start, end: range.end })
    }
  }
  return merged
}

/** Minutes of the day as "HH:mm". */
export function clockOf(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

export interface PlacedItem<T> {
  item: T
  start: number
  end: number
  /** Zero-based column within its group of overlapping items, and the group's column count. */
  lane: number
  lanes: number
}

/** Side-by-side layout of overlapping items within one day column. */
export function layoutLanes<T>(spans: { item: T; start: number; end: number }[]): PlacedItem<T>[] {
  const sorted = [...spans].sort((a, b) => a.start - b.start || b.end - a.end)
  const placed: PlacedItem<T>[] = []
  let group: PlacedItem<T>[] = []
  let groupEnd = -1
  const closeGroup = () => {
    const lanes = Math.max(1, ...group.map((entry) => entry.lane + 1))
    group.forEach((entry) => (entry.lanes = lanes))
    group = []
  }
  for (const span of sorted) {
    if (span.start >= groupEnd) {
      closeGroup()
      groupEnd = span.end
    }
    const taken = new Set(group.filter((entry) => entry.end > span.start).map((entry) => entry.lane))
    let lane = 0
    while (taken.has(lane)) lane += 1
    const entry = { ...span, lane, lanes: 1 }
    group.push(entry)
    placed.push(entry)
    groupEnd = Math.max(groupEnd, span.end)
  }
  closeGroup()
  return placed
}
