import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { AppointmentStatus } from '../../shared/domain/appointmentStatus'
import type { ActivityItem } from '../activity/api'
import type { AppointmentType, NameRef } from '../calendar/api'

/** Today's appointments by progress; cancelled ones are excluded from `appointments` and `patients`. */
export interface DaySummary {
  appointments: number
  patients: number
  /** Still SCHEDULED or CONFIRMED. */
  remaining: number
  waiting: number
  inConsultation: number
  completed: number
  noShows: number
  cancelled: number
}

export interface ScheduleItem {
  id: string
  startAt: string
  endAt: string
  patient: NameRef
  doctor: NameRef
  type: AppointmentType
  status: AppointmentStatus
  /** Doctor dashboard only, for authorized patients; otherwise null. */
  alerts: { severeAllergies: string[]; activeConditions: string[] } | null
}

/** GET /dashboard/owner (api.md §22). */
export interface OwnerDashboard {
  timeZone: string
  today: string
  summary: DaySummary
  activePatients: number
  activeDoctors: number
  doctors: {
    doctor: NameRef
    appointments: number
    completed: number
    /** WAITING or IN_CONSULTATION. */
    inProgress: number
    remaining: number
    noShows: number
  }[]
  schedule: ScheduleItem[]
  scheduleTruncated: boolean
  recentActivity: ActivityItem[]
}

/** GET /dashboard/doctor (api.md §22): the caller's own day only. */
export interface DoctorDashboard {
  timeZone: string
  today: string
  doctor: NameRef
  summary: DaySummary
  /** SCHEDULED or CONFIRMED in the seven days after today. */
  upcomingAppointments: number
  patients: number
  primaryPatients: number
  schedule: ScheduleItem[]
  recentPatients: { patient: NameRef; lastVisitAt: string }[]
  recentActivity: ActivityItem[]
}

/** Dashboards are a snapshot of today; refreshed every minute while open. */
const REFRESH_MS = 60_000

export function useOwnerDashboard() {
  return useQuery({
    queryKey: ['dashboard', 'owner'],
    queryFn: () => apiRequest<OwnerDashboard>('/dashboard/owner'),
    refetchInterval: REFRESH_MS,
  })
}

export function useDoctorDashboard() {
  return useQuery({
    queryKey: ['dashboard', 'doctor'],
    queryFn: () => apiRequest<DoctorDashboard>('/dashboard/doctor'),
    refetchInterval: REFRESH_MS,
  })
}
