/**
 * Calendar dates ("YYYY-MM-DD") and wall-clock times ("HH:mm") as the API sends them for schedules: already in the
 * clinic's time zone, so they are handled as UTC values and never shifted by the device's zone.
 */

/** Today's date in a time zone (the device's when unknown). */
export function todayIn(timeZone: string | null | undefined): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: timeZone ?? undefined,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

export function addDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}

export function dateValue(date: string): Date {
  return new Date(`${date}T00:00:00Z`)
}

export function timeValue(time: string): Date {
  return new Date(`1970-01-01T${time}:00Z`)
}

/** An instant as the "YYYY-MM-DDTHH:mm" wall-clock time of a time zone (a datetime-local input value). */
export function localDateTimeOf(instant: string | Date, timeZone: string | null | undefined): string {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: timeZone ?? undefined,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(instant))
      .map((part) => [part.type, part.value]),
  )
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

/** The ISO instant of a "YYYY-MM-DDTHH:mm" wall-clock time in a time zone. */
export function instantOf(localDateTime: string, timeZone: string | null | undefined): string {
  const wallClock = Date.parse(`${localDateTime}:00Z`)
  const offsetAt = (instant: number) => Date.parse(`${localDateTimeOf(new Date(instant), timeZone)}:00Z`) - instant
  // A second pass settles times next to a daylight-saving transition.
  const firstGuess = wallClock - offsetAt(wallClock)
  return new Date(wallClock - offsetAt(firstGuess)).toISOString()
}

/** Minutes since midnight, for comparing "HH:mm" values. */
export function minutesOf(time: string): number {
  const [hours = 0, minutes = 0] = time.split(':').map(Number)
  return hours * 60 + minutes
}
