import type { ActivityItem, KpiData, KpiFigures } from './api'

export const daniel = {
  id: '0190a000-0000-7000-8000-000000000002',
  firstName: 'Daniel',
  lastName: 'Brown',
}
export const chloe = {
  id: '0190a000-0000-7000-8000-0000000000d2',
  firstName: 'Chloe',
  lastName: 'Carter',
}
export const alice = {
  id: '0190a000-0000-7000-8000-0000000000p1',
  firstName: 'Alice',
  lastName: 'Martin',
}
export const sara = {
  id: '0190a000-0000-7000-8000-0000000000u1',
  firstName: 'Sara',
  lastName: 'Lopez',
}

export function figures(overrides: Partial<KpiFigures> = {}): KpiFigures {
  return {
    appointments: 40,
    appointmentsByStatus: {
      SCHEDULED: 4,
      CONFIRMED: 2,
      WAITING: 0,
      IN_CONSULTATION: 0,
      COMPLETED: 28,
      CANCELLED: 4,
      NO_SHOW: 2,
    },
    completionRate: 0.82,
    noShowRate: 0.07,
    cancellationRate: 0.1,
    newPatients: 6,
    activePatients: 120,
    consultations: 27,
    ...overrides,
  }
}

export function kpis(overrides: Partial<KpiData> = {}): KpiData {
  return {
    from: '2026-09-11',
    to: '2026-10-10',
    previousFrom: '2026-08-12',
    previousTo: '2026-09-10',
    doctor: null,
    current: figures(),
    previous: figures({
      appointments: 32,
      completionRate: 0.75,
      newPatients: 6,
    }),
    trendBucket: 'DAY',
    trend: [
      {
        date: '2026-10-09',
        appointments: 3,
        completed: 2,
        cancelled: 1,
        noShows: 0,
      },
      {
        date: '2026-10-10',
        appointments: 2,
        completed: 1,
        cancelled: 0,
        noShows: 1,
      },
    ],
    doctors: [],
    ...overrides,
  }
}

export function activityItem(overrides: Partial<ActivityItem> = {}): ActivityItem {
  return {
    id: '0190a000-0000-7000-8000-0000000000e1',
    occurredAt: '2026-10-10T07:15:00Z',
    action: 'APPOINTMENT_CREATED',
    category: 'APPOINTMENTS',
    actor: sara,
    entityType: 'APPOINTMENT',
    patient: alice,
    doctor: daniel,
    staff: null,
    appointmentId: '0190a000-0000-7000-8000-0000000000a1',
    status: null,
    ...overrides,
  }
}
