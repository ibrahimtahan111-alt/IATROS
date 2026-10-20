import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import type { Role } from '../auth/api'

/** api.md §8. `id` is the doctor id used by schedules and appointments; `userId` is the staff account. */
export interface Doctor {
  id: string
  userId: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  role: Role
  status: 'INVITED' | 'ACTIVE' | 'DEACTIVATED'
  primarySpecialty: string
  additionalSpecialty: string | null
  registrationNumber: string | null
  version: number
}

export interface UpdateDoctorProfileRequest {
  primarySpecialty: string
  additionalSpecialty: string | null
  registrationNumber: string | null
  version: number
}

export const doctorKeys = {
  all: ['doctors'] as const,
  choices: ['doctors', 'choices'] as const,
  detail: (id: string) => ['doctors', 'detail', id] as const,
}

/** Every doctor the caller may see, for pickers. Clinics are small (D-001), so one page of the maximum size is enough. */
export function useDoctorChoices(enabled = true) {
  return useQuery({
    queryKey: doctorKeys.choices,
    queryFn: () => apiRequest<PageResponse<Doctor>>('/doctors?size=100'),
    select: (page) => page.content,
    enabled,
  })
}

export function useDoctor(id: string | null | undefined) {
  return useQuery({
    queryKey: doctorKeys.detail(id ?? ''),
    queryFn: () => apiRequest<Doctor>(`/doctors/${id}`),
    enabled: Boolean(id),
  })
}

/** The team pages show the same profile, so their cached members are refreshed too. */
export function useUpdateDoctorProfile(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateDoctorProfileRequest) =>
      apiRequest<Doctor>(`/doctors/${id}`, { method: 'PUT', body: request }),
    onSuccess: (doctor) => {
      queryClient.setQueryData(doctorKeys.detail(doctor.id), doctor)
      void queryClient.invalidateQueries({ queryKey: doctorKeys.choices })
      void queryClient.invalidateQueries({ queryKey: ['staff'] })
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: doctorKeys.detail(id) }),
  })
}

export function doctorName(doctor: Pick<Doctor, 'firstName' | 'lastName'>): string {
  return `${doctor.firstName} ${doctor.lastName}`
}
