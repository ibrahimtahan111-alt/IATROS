import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { dateValue, timeValue } from '../lib/localDates'

/** 2024-01-01 was a Monday; used to name ISO weekdays without hardcoded strings. */
const A_MONDAY = Date.UTC(2024, 0, 1)

/**
 * Formats API instants in the interface language and the clinic's time zone (internationalization.md), so every
 * user of a clinic sees the same wall-clock times regardless of their device settings. Calendar dates and
 * wall-clock times (schedules) are already clinic-local and are formatted without any zone conversion.
 */
export function useDateTimeFormat(timeZone: string | null | undefined) {
  const { i18n } = useTranslation()
  const language = i18n.language

  return useMemo(() => {
    const zone = timeZone ?? undefined
    const date = new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeZone: zone })
    const dateTime = new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeStyle: 'short', timeZone: zone })
    const localDate = new Intl.DateTimeFormat(language, { dateStyle: 'medium', timeZone: 'UTC' })
    const weekdayDate = new Intl.DateTimeFormat(language, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    })
    const localTime = new Intl.DateTimeFormat(language, { timeStyle: 'short', timeZone: 'UTC' })
    const instantTime = new Intl.DateTimeFormat(language, { timeStyle: 'short', timeZone: zone })
    const weekday = new Intl.DateTimeFormat(language, { weekday: 'long', timeZone: 'UTC' })
    const shortWeekday = new Intl.DateTimeFormat(language, { weekday: 'short', timeZone: 'UTC' })
    const longDate = new Intl.DateTimeFormat(language, { dateStyle: 'full', timeZone: 'UTC' })
    const monthYear = new Intl.DateTimeFormat(language, { month: 'long', year: 'numeric', timeZone: 'UTC' })
    return {
      date: (iso: string) => date.format(new Date(iso)),
      dateTime: (iso: string) => dateTime.format(new Date(iso)),
      /** Time of day of an instant, in the clinic's zone. */
      timeOf: (iso: string) => instantTime.format(new Date(iso)),
      timeRangeOf: (start: string, end: string) => instantTime.formatRange(new Date(start), new Date(end)),
      /** "YYYY-MM-DD" in full, e.g. "Monday, October 12, 2026". */
      longDate: (value: string) => longDate.format(dateValue(value)),
      /** Month and year of a "YYYY-MM-DD" date, e.g. "October 2026". */
      monthYear: (value: string) => monthYear.format(dateValue(value)),
      /** Range of "YYYY-MM-DD" dates, e.g. "Oct 12 – 18, 2026". */
      localDateRange: (from: string, to: string) => localDate.formatRange(dateValue(from), dateValue(to)),
      /** "YYYY-MM-DD" calendar date. */
      localDate: (value: string) => localDate.format(dateValue(value)),
      /** "YYYY-MM-DD" with its weekday, e.g. "Mon, Oct 12". */
      weekdayDate: (value: string) => weekdayDate.format(dateValue(value)),
      /** "HH:mm" wall-clock time. */
      time: (value: string) => localTime.format(timeValue(value)),
      timeRange: (start: string, end: string) => localTime.formatRange(timeValue(start), timeValue(end)),
      /** ISO day of week (1 = Monday … 7 = Sunday). */
      weekday: (isoDay: number) => weekday.format(new Date(A_MONDAY + (isoDay - 1) * 86_400_000)),
      shortWeekday: (isoDay: number) => shortWeekday.format(new Date(A_MONDAY + (isoDay - 1) * 86_400_000)),
    }
  }, [language, timeZone])
}
