import { z } from 'zod/mini'
import { emptyToNull, optionalText } from '../../shared/forms/fields'
import { instantOf } from '../../shared/lib/localDates'
import {
  APPOINTMENT_TYPES,
  type Appointment,
  type AppointmentDetailsRequest,
  type CreateAppointmentRequest,
  type RescheduleRequest,
} from './api'

/** Durations offered, in minutes; the clinic's default and the current value are added if they are not among them. */
export const DURATIONS = [10, 15, 20, 30, 45, 60, 90, 120] as const

export function durationsWith(...extra: number[]): number[] {
  return [...new Set([...DURATIONS, ...extra.filter((minutes) => minutes > 0)])].sort((a, b) => a - b)
}

const required = z.string().check(z.minLength(1, { error: 'REQUIRED' }))
const date = z.string().check(z.regex(/^\d{4}-\d{2}-\d{2}$/, { error: 'REQUIRED' }))
const time = z.string().check(z.regex(/^\d{2}:\d{2}$/, { error: 'REQUIRED' }))

const timeFields = {
  doctorId: required,
  date,
  time,
  duration: required,
}

const detailFields = {
  type: z.enum(APPOINTMENT_TYPES),
  reason: optionalText(500),
  notes: optionalText(4000),
}

export const bookingSchema = z.object({ patientId: required, ...timeFields, ...detailFields })
export type BookingValues = z.infer<typeof bookingSchema>

export const rescheduleSchema = z.object(timeFields)
export type RescheduleValues = z.infer<typeof rescheduleSchema>

export const detailsSchema = z.object(detailFields)
export type DetailsValues = z.infer<typeof detailsSchema>

export const cancelSchema = z.object({ reason: optionalText(500) })
export type CancelValues = z.infer<typeof cancelSchema>

/** Start and end instants of a clinic-local date, time, and duration. */
export function timesOf(values: RescheduleValues, timeZone: string | null): RescheduleRequest {
  const startAt = instantOf(`${values.date}T${values.time}`, timeZone)
  const endAt = new Date(Date.parse(startAt) + Number(values.duration) * 60_000).toISOString()
  return { doctorId: values.doctorId, startAt, endAt }
}

export function toBookingRequest(values: BookingValues, timeZone: string | null): CreateAppointmentRequest {
  return { patientId: values.patientId, ...timesOf(values, timeZone), ...toDetailsRequest(values) }
}

export function toDetailsRequest(values: DetailsValues): AppointmentDetailsRequest {
  return { type: values.type, reason: emptyToNull(values.reason), notes: emptyToNull(values.notes) }
}

export function detailsValuesOf(appointment: Appointment): DetailsValues {
  return { type: appointment.type, reason: appointment.reason ?? '', notes: appointment.notes ?? '' }
}

/** Where the server's field errors belong on these forms, which split the start instant into date and time. */
export const SERVER_FIELDS: Record<string, string> = { startAt: 'time', endAt: 'duration' }
