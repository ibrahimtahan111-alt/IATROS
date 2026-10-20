import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { Language } from '../../i18n/languages'
import { currentUserKey } from '../auth/api'

/** GET /api/v1/clinic (api.md §6). Profile and settings are versioned separately. */
export interface Clinic {
  id: string
  name: string
  email: string | null
  phone: string | null
  address: string | null
  version: number
  settings: ClinicSettings
}

export interface ClinicSettings {
  timeZone: string
  defaultAppointmentMinutes: number
  defaultLanguage: Language
  /** ISO-8601: 1 = Monday … 7 = Sunday. */
  weekStartDay: number
  version: number
}

export interface UpdateClinicRequest {
  name: string
  email: string | null
  phone: string | null
  address: string | null
  version: number
}

export type UpdateClinicSettingsRequest = ClinicSettings

const clinicKey = ['clinic'] as const

export function useClinic() {
  return useQuery({ queryKey: clinicKey, queryFn: () => apiRequest<Clinic>('/clinic') })
}

/** The session user carries the clinic name, time zone, and default language, so it is refreshed too. */
function useClinicMutation<V>(request: (variables: V) => Promise<Clinic>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (clinic) => {
      queryClient.setQueryData(clinicKey, clinic)
      void queryClient.invalidateQueries({ queryKey: currentUserKey })
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: clinicKey }),
  })
}

export function useUpdateClinic() {
  return useClinicMutation((request: UpdateClinicRequest) =>
    apiRequest<Clinic>('/clinic', { method: 'PUT', body: request }),
  )
}

export function useUpdateClinicSettings() {
  return useClinicMutation((request: UpdateClinicSettingsRequest) =>
    apiRequest<Clinic>('/clinic/settings', { method: 'PUT', body: request }),
  )
}
