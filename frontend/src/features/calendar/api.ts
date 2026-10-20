import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import type { AppointmentStatus } from '../../shared/domain/appointmentStatus'

/** Stable appointment types (decisions.md D-023). Labels come from `calendar:type.*`. */
export const APPOINTMENT_TYPES = ['NEW_PATIENT', 'FOLLOW_UP', 'ROUTINE', 'PROCEDURE', 'URGENT', 'OTHER'] as const
export type AppointmentType = (typeof APPOINTMENT_TYPES)[number]

/** A patient, doctor, or user shown on an appointment: booking-level data only (D-029). */
export interface NameRef {
  id: string
  firstName: string
  lastName: string
}

export interface TimeRange {
  /** "HH:mm", clinic-local. */
  start: string
  end: string
}

export interface CalendarEvent {
  id: string
  startAt: string
  endAt: string
  doctorId: string
  patient: NameRef
  type: AppointmentType
  status: AppointmentStatus
}

export interface DoctorHours extends NameRef {
  days: { date: string; ranges: TimeRange[] }[]
}

/** GET /calendar/events (api.md §19): events plus each shown doctor's working hours for the range. */
export interface CalendarData {
  timeZone: string
  from: string
  to: string
  events: CalendarEvent[]
  doctors: DoctorHours[]
}

/** One appointment and what the caller may do with it, as decided by the server (D-038). */
export interface Appointment {
  id: string
  patient: NameRef
  doctor: NameRef
  startAt: string
  endAt: string
  type: AppointmentType
  status: AppointmentStatus
  reason: string | null
  notes: string | null
  cancellationReason: string | null
  statusChangedAt: string | null
  createdBy: NameRef | null
  createdAt: string
  updatedAt: string
  /** Details and time can still change. */
  editable: boolean
  cancellable: boolean
  /** Status changes allowed to the caller, cancellation excluded. */
  nextStatuses: AppointmentStatus[]
  /** Only sent to callers with clinical access. */
  consultationId: string | null
  version: number
}

export interface AppointmentSummary {
  id: string
  startAt: string
  endAt: string
  patient: NameRef
  doctor: NameRef
  type: AppointmentType
  status: AppointmentStatus
  reason: string | null
}

export interface AppointmentDetailsRequest {
  type: AppointmentType
  reason: string | null
  notes: string | null
}

export interface CreateAppointmentRequest extends AppointmentDetailsRequest {
  patientId: string
  doctorId: string
  startAt: string
  endAt: string
}

export interface RescheduleRequest {
  doctorId: string
  startAt: string
  endAt: string
}

export interface Availability {
  timeZone: string
  date: string
  durationMinutes: number
  slots: { startAt: string; endAt: string }[]
}

export const PATIENT_APPOINTMENTS_PAGE_SIZE = 10

const appointmentKeys = {
  calendar: ['calendar'] as const,
  range: (from: string, to: string, doctorId: string | null) => ['calendar', from, to, doctorId] as const,
  lists: ['appointments', 'list'] as const,
  patient: (patientId: string, page: number) => ['appointments', 'list', 'patient', patientId, page] as const,
  detail: (id: string) => ['appointments', 'detail', id] as const,
  availability: ['appointments', 'availability'] as const,
}

export function useCalendar(from: string, to: string, doctorId: string | null) {
  const query = new URLSearchParams({ from, to })
  if (doctorId) query.append('doctorId', doctorId)
  return useQuery({
    queryKey: appointmentKeys.range(from, to, doctorId),
    queryFn: () => apiRequest<CalendarData>(`/calendar/events?${query}`),
    placeholderData: keepPreviousData,
  })
}

export function useAppointment(id: string | null) {
  return useQuery({
    queryKey: appointmentKeys.detail(id ?? ''),
    queryFn: () => apiRequest<Appointment>(`/appointments/${id}`),
    enabled: Boolean(id),
  })
}

/** A patient's appointments, latest first; doctors receive only their own (D-038). */
export function usePatientAppointments(patientId: string, page: number) {
  return useQuery({
    queryKey: appointmentKeys.patient(patientId, page),
    queryFn: () =>
      apiRequest<PageResponse<AppointmentSummary>>(
        `/appointments?patientId=${patientId}&page=${page}&size=${PATIENT_APPOINTMENTS_PAGE_SIZE}`,
      ),
    placeholderData: keepPreviousData,
  })
}

/** Free start times of a doctor on a date; the server is the only authority on availability (D-035). */
export function useAvailability(doctorId: string, date: string, durationMinutes: number) {
  const enabled = Boolean(doctorId) && /^\d{4}-\d{2}-\d{2}$/.test(date) && durationMinutes > 0
  return useQuery({
    queryKey: [...appointmentKeys.availability, doctorId, date, durationMinutes],
    queryFn: () =>
      apiRequest<Availability>(
        `/appointments/availability?doctorId=${doctorId}&date=${date}&duration=${durationMinutes}`,
      ),
    enabled,
  })
}

/**
 * Every appointment change refreshes the calendar, the lists, availability, and the patients' next appointment.
 * A failure (e.g. CONCURRENT_MODIFICATION) refreshes the appointment so the next attempt uses current data.
 */
function useAppointmentMutation<V>(request: (variables: V) => Promise<Appointment>, id?: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (appointment) => {
      queryClient.setQueryData(appointmentKeys.detail(appointment.id), appointment)
      void queryClient.invalidateQueries({ queryKey: appointmentKeys.calendar })
      void queryClient.invalidateQueries({ queryKey: appointmentKeys.lists })
      void queryClient.invalidateQueries({ queryKey: appointmentKeys.availability })
      void queryClient.invalidateQueries({ queryKey: ['patients'] })
    },
    onError: () => {
      if (id) void queryClient.invalidateQueries({ queryKey: appointmentKeys.detail(id) })
      void queryClient.invalidateQueries({ queryKey: appointmentKeys.calendar })
      void queryClient.invalidateQueries({ queryKey: appointmentKeys.availability })
    },
  })
}

export function useCreateAppointment() {
  return useAppointmentMutation((request: CreateAppointmentRequest) =>
    apiRequest<Appointment>('/appointments', { method: 'POST', body: request }),
  )
}

export function useUpdateAppointment(id: string) {
  return useAppointmentMutation(
    (request: AppointmentDetailsRequest & { version: number }) =>
      apiRequest<Appointment>(`/appointments/${id}`, { method: 'PUT', body: request }),
    id,
  )
}

export function useRescheduleAppointment(id: string) {
  return useAppointmentMutation(
    (request: RescheduleRequest & { version: number }) =>
      apiRequest<Appointment>(`/appointments/${id}/schedule`, { method: 'PATCH', body: request }),
    id,
  )
}

export function useChangeAppointmentStatus(id: string) {
  return useAppointmentMutation(
    (request: { status: AppointmentStatus; version: number }) =>
      apiRequest<Appointment>(`/appointments/${id}/status`, { method: 'PATCH', body: request }),
    id,
  )
}

export function useCancelAppointment(id: string) {
  return useAppointmentMutation(
    (request: { reason: string | null; version: number }) =>
      apiRequest<Appointment>(`/appointments/${id}/cancel`, { method: 'POST', body: request }),
    id,
  )
}

export function personName(person: Pick<NameRef, 'firstName' | 'lastName'>): string {
  return `${person.firstName} ${person.lastName}`
}
