import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'

/** ISO day of week: 1 = Monday … 7 = Sunday. Times are "HH:mm" in the clinic's time zone (api.md §20). */
export interface SchedulePeriod {
  dayOfWeek: number
  startTime: string
  endTime: string
}

export interface WeeklySchedule {
  doctorId: string
  timeZone: string
  version: number
  periods: SchedulePeriod[]
}

export type ScheduleExceptionType = 'UNAVAILABLE' | 'EXTRA_AVAILABILITY'

export interface ScheduleException {
  id: string
  date: string
  type: ScheduleExceptionType
  /** Both null for an all-day unavailability. */
  startTime: string | null
  endTime: string | null
  reason: string | null
  createdAt: string
}

export interface CreateScheduleExceptionRequest {
  date: string
  type: ScheduleExceptionType
  startTime: string | null
  endTime: string | null
  reason: string | null
}

export interface WorkingHours {
  timeZone: string
  days: { date: string; ranges: { start: string; end: string }[] }[]
}

const scheduleKeys = {
  doctor: (doctorId: string) => ['schedules', doctorId] as const,
  weekly: (doctorId: string) => ['schedules', doctorId, 'weekly'] as const,
  exceptions: (doctorId: string) => ['schedules', doctorId, 'exceptions'] as const,
  workingHours: (doctorId: string, from: string, to: string) => ['schedules', doctorId, 'working-hours', from, to] as const,
}

export function useWeeklySchedule(doctorId: string) {
  return useQuery({
    queryKey: scheduleKeys.weekly(doctorId),
    queryFn: () => apiRequest<WeeklySchedule>(`/doctors/${doctorId}/schedule`),
  })
}

export function useScheduleExceptions(doctorId: string) {
  return useQuery({
    queryKey: scheduleKeys.exceptions(doctorId),
    queryFn: () => apiRequest<ScheduleException[]>(`/doctors/${doctorId}/schedule/exceptions`),
  })
}

export function useWorkingHours(doctorId: string, from: string, to: string) {
  return useQuery({
    queryKey: scheduleKeys.workingHours(doctorId, from, to),
    queryFn: () => apiRequest<WorkingHours>(`/doctors/${doctorId}/working-hours?from=${from}&to=${to}`),
    placeholderData: keepPreviousData,
  })
}

/** Any change can move the doctor's effective hours, so everything cached for the doctor is refreshed. */
function useScheduleMutation<V, R>(doctorId: string, request: (variables: V) => Promise<R>, onSuccess?: (result: R) => void) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess,
    onSettled: () => queryClient.invalidateQueries({ queryKey: scheduleKeys.doctor(doctorId) }),
  })
}

export function useReplaceWeeklySchedule(doctorId: string) {
  const queryClient = useQueryClient()
  return useScheduleMutation(
    doctorId,
    (request: { periods: SchedulePeriod[]; version: number }) =>
      apiRequest<WeeklySchedule>(`/doctors/${doctorId}/schedule`, { method: 'PUT', body: request }),
    (schedule) => queryClient.setQueryData(scheduleKeys.weekly(doctorId), schedule),
  )
}

export function useCreateScheduleException(doctorId: string) {
  return useScheduleMutation(doctorId, (request: CreateScheduleExceptionRequest) =>
    apiRequest<ScheduleException>(`/doctors/${doctorId}/schedule/exceptions`, { method: 'POST', body: request }),
  )
}

export function useDeleteScheduleException(doctorId: string) {
  return useScheduleMutation(doctorId, (exceptionId: string) =>
    apiRequest<void>(`/doctors/${doctorId}/schedule/exceptions/${exceptionId}`, { method: 'DELETE' }),
  )
}
