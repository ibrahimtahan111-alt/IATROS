import { z } from 'zod/mini'
import { emptyToNull, optionalText } from '../../shared/forms/fields'
import { minutesOf } from '../../shared/lib/localDates'
import type { CreateScheduleExceptionRequest, ScheduleExceptionType, WeeklySchedule } from './api'

const requiredTime = () => z.string().check(z.minLength(1, { error: 'REQUIRED' }))

/**
 * Mirrors the server rules (decisions.md D-035) with the same codes and period indexes, so client and server errors
 * land on the same inputs: a period ends after it starts; periods of a day may touch but not overlap.
 */
export const weekSchema = z
  .object({
    /** The version the form was loaded from; a concurrent change makes the save fail instead of overwriting it. */
    version: z.number(),
    periods: z.array(z.object({ dayOfWeek: z.number(), startTime: requiredTime(), endTime: requiredTime() })),
  })
  .check(
    z.superRefine(({ periods }, ctx) => {
      periods.forEach((period, index) => {
        if (period.startTime && period.endTime && minutesOf(period.endTime) <= minutesOf(period.startTime)) {
          ctx.addIssue({ code: 'custom', message: 'END_BEFORE_START', path: ['periods', index, 'endTime'], input: period })
        }
      })
      const order = periods
        .map((period, index) => ({ period, index }))
        .filter(({ period }) => period.startTime && period.endTime)
        .sort((a, b) => a.period.dayOfWeek - b.period.dayOfWeek || minutesOf(a.period.startTime) - minutesOf(b.period.startTime))
      for (let k = 1; k < order.length; k++) {
        const previous = order[k - 1]!.period
        const { period, index } = order[k]!
        if (previous.dayOfWeek === period.dayOfWeek && minutesOf(period.startTime) < minutesOf(previous.endTime)) {
          ctx.addIssue({ code: 'custom', message: 'OVERLAP', path: ['periods', index, 'startTime'], input: period })
        }
      }
    }),
  )

export type WeekValues = z.infer<typeof weekSchema>

export function weekValuesOf(schedule: WeeklySchedule): WeekValues {
  return { version: schedule.version, periods: schedule.periods.map((period) => ({ ...period })) }
}

export const exceptionSchema = z
  .object({
    date: z.string().check(z.minLength(1, { error: 'REQUIRED' })),
    type: z.enum(['UNAVAILABLE', 'EXTRA_AVAILABILITY']),
    startTime: z.string(),
    endTime: z.string(),
    reason: optionalText(500),
  })
  .check(
    z.superRefine((values, ctx) => {
      const timed = values.type === 'EXTRA_AVAILABILITY' || values.startTime !== '' || values.endTime !== ''
      if (timed && values.startTime === '') {
        ctx.addIssue({ code: 'custom', message: 'REQUIRED', path: ['startTime'], input: values })
      }
      if (timed && values.endTime === '') {
        ctx.addIssue({ code: 'custom', message: 'REQUIRED', path: ['endTime'], input: values })
      }
      if (values.startTime && values.endTime && minutesOf(values.endTime) <= minutesOf(values.startTime)) {
        ctx.addIssue({ code: 'custom', message: 'END_BEFORE_START', path: ['endTime'], input: values })
      }
    }),
  )

export type ExceptionValues = z.infer<typeof exceptionSchema>

export function emptyException(date: string, type: ScheduleExceptionType = 'UNAVAILABLE'): ExceptionValues {
  return { date, type, startTime: '', endTime: '', reason: '' }
}

export function toCreateExceptionRequest(values: ExceptionValues): CreateScheduleExceptionRequest {
  return {
    date: values.date,
    type: values.type,
    startTime: emptyToNull(values.startTime),
    endTime: emptyToNull(values.endTime),
    reason: emptyToNull(values.reason),
  }
}
