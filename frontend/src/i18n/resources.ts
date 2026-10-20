import type enAccount from './locales/en/account.json'
import type enActivity from './locales/en/activity.json'
import enAuth from './locales/en/auth.json'
import type enCalendar from './locales/en/calendar.json'
import type enClinic from './locales/en/clinic.json'
import type enDashboard from './locales/en/dashboard.json'
import type enDoctors from './locales/en/doctors.json'
import type enMedicalRecords from './locales/en/medicalRecords.json'
import type enPatients from './locales/en/patients.json'
import type enSchedules from './locales/en/schedules.json'
import enCommon from './locales/en/common.json'
import enErrors from './locales/en/errors.json'
import type enSpecialties from './locales/en/specialties.json'
import type enTeam from './locales/en/team.json'

export const defaultNamespace = 'common'

/**
 * Bundled with the app: needed before and right after sign-in. Only the default language ships in the sign-in
 * bundle; other languages' core namespaces are fetched when first used (see index.ts).
 */
export const resources = {
  en: { common: enCommon, auth: enAuth, errors: enErrors },
} as const

export const namespaces = Object.keys(resources.en) as (keyof (typeof resources)['en'])[]

/** Feature namespaces, loaded with the route that needs them (see loadNamespaces). Types only here. */
export interface LazyResources {
  account: typeof enAccount
  activity: typeof enActivity
  calendar: typeof enCalendar
  clinic: typeof enClinic
  dashboard: typeof enDashboard
  doctors: typeof enDoctors
  medicalRecords: typeof enMedicalRecords
  patients: typeof enPatients
  schedules: typeof enSchedules
  specialties: typeof enSpecialties
  team: typeof enTeam
}

export type LazyNamespace = keyof LazyResources

export type AllResources = (typeof resources)['en'] & LazyResources
