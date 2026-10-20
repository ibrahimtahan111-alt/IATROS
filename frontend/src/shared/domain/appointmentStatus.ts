/** Language-independent appointment statuses (decisions.md D-024). Labels come from `appointmentStatus.*`. */
export const APPOINTMENT_STATUSES = [
  'SCHEDULED',
  'CONFIRMED',
  'WAITING',
  'IN_CONSULTATION',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
] as const

export type AppointmentStatus = (typeof APPOINTMENT_STATUSES)[number]
