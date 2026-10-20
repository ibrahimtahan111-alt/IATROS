import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import type { AppointmentStatus } from '../../shared/domain/appointmentStatus'
import type { NameRef } from '../calendar/api'

/** Activity feed types (api.md §24, D-039). Labels come from `activity:category.*`. */
export const ACTIVITY_CATEGORIES = ['APPOINTMENTS', 'PATIENTS', 'CLINICAL', 'SCHEDULES', 'TEAM', 'CLINIC'] as const
export type ActivityCategory = (typeof ACTIVITY_CATEGORIES)[number]

/** One activity entry: names and codes only, never notes or medical content. */
export interface ActivityItem {
  id: string
  occurredAt: string
  /** Stable audit action code, e.g. APPOINTMENT_CREATED; the sentence comes from `activity:event.*`. */
  action: string
  category: ActivityCategory
  /** Null for system events. */
  actor: NameRef | null
  entityType: string
  /** Null when the caller may not see the patient. */
  patient: NameRef | null
  doctor: NameRef | null
  staff: NameRef | null
  appointmentId: string | null
  status: AppointmentStatus | null
}

export interface KpiFigures {
  appointments: number
  appointmentsByStatus: Record<AppointmentStatus, number>
  /** Fractions between 0 and 1; null when there is nothing to divide by (D-031). */
  completionRate: number | null
  noShowRate: number | null
  cancellationRate: number | null
  newPatients: number
  activePatients: number
  consultations: number
}

export interface TrendPoint {
  /** First day of the bucket. */
  date: string
  appointments: number
  completed: number
  cancelled: number
  noShows: number
}

export interface DoctorFigures {
  doctor: NameRef
  appointments: number
  completed: number
  cancelled: number
  noShows: number
  completionRate: number | null
  consultations: number
}

/** GET /kpis/clinic and /kpis/doctors/{id} (api.md §23). */
export interface KpiData {
  from: string
  to: string
  previousFrom: string
  previousTo: string
  doctor: NameRef | null
  current: KpiFigures
  previous: KpiFigures
  trendBucket: 'DAY' | 'MONTH'
  trend: TrendPoint[]
  /** Clinic KPIs only. */
  doctors: DoctorFigures[]
}

/** Period presets: the last N days up to today, clinic-local. */
export const KPI_PERIODS = {
  week: 7,
  month: 30,
  quarter: 90,
  year: 365,
} as const
export type KpiPeriod = keyof typeof KPI_PERIODS

export interface ActivityQuery {
  from: string | null
  to: string | null
  categories: ActivityCategory[]
  doctorId: string | null
  page: number
  size: number
}

const insightKeys = {
  kpis: (scope: string, from: string, to: string) => ['kpis', scope, from, to] as const,
  activity: (query: ActivityQuery) => ['activity', query] as const,
}

/**
 * Clinic KPIs for owners, or one doctor's KPIs when {@code doctorId} is set (their own for doctors). The server
 * refuses anything else (security.md §40).
 */
export function useKpis(doctorId: string | null, from: string, to: string, enabled = true) {
  const path = doctorId ? `/kpis/doctors/${doctorId}` : '/kpis/clinic'
  return useQuery({
    queryKey: insightKeys.kpis(doctorId ?? 'clinic', from, to),
    queryFn: () => apiRequest<KpiData>(`${path}?from=${from}&to=${to}`),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useActivity(query: ActivityQuery) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  })
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.doctorId) params.set('doctorId', query.doctorId)
  for (const category of query.categories) params.append('type', category)
  return useQuery({
    queryKey: insightKeys.activity(query),
    queryFn: () => apiRequest<PageResponse<ActivityItem>>(`/activity?${params}`),
    placeholderData: keepPreviousData,
  })
}
