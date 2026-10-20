import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import { listQuery, type ListParams } from '../../shared/hooks/useListSearchParams'

export type PatientStatus = 'ACTIVE' | 'ARCHIVED'
export type Gender = 'FEMALE' | 'MALE' | 'OTHER' | 'UNDISCLOSED'
export type PatientFilter = 'primaryDoctorId' | 'status'

export const GENDERS: readonly Gender[] = ['FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED']

/** A doctor with access to a patient (api.md §10). */
export interface PatientDoctor {
  id: string
  firstName: string
  lastName: string
  status: 'INVITED' | 'ACTIVE' | 'DEACTIVATED'
  relationshipType: 'PRIMARY' | 'AUTHORIZED'
}

export interface PatientSummary {
  id: string
  firstName: string
  lastName: string
  dateOfBirth: string
  gender: Gender | null
  phone: string | null
  email: string | null
  status: PatientStatus
  primaryDoctor: PatientDoctor | null
  /** Within the appointments the caller may see (D-038). */
  nextAppointmentAt: string | null
  lastVisitAt: string | null
}

export interface Patient extends PatientSummary {
  address: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  archivedAt: string | null
  createdAt: string
  version: number
  /** Every doctor with access, primary first. Null for secretaries, who only see the primary doctor (D-029). */
  doctors: PatientDoctor[] | null
}

export interface PatientDetailsRequest {
  firstName: string
  lastName: string
  dateOfBirth: string
  gender: Gender | null
  phone: string | null
  email: string | null
  address: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
}

export interface CreatePatientRequest extends PatientDetailsRequest {
  primaryDoctorId: string
}

export interface UpdatePatientRequest extends PatientDetailsRequest {
  version: number
}

const patientKeys = {
  all: ['patients'] as const,
  list: (params: ListParams<PatientFilter>) => ['patients', 'list', params] as const,
  detail: (id: string) => ['patients', 'detail', id] as const,
}

export function usePatientList(params: ListParams<PatientFilter>) {
  return useQuery({
    queryKey: patientKeys.list(params),
    queryFn: () => apiRequest<PageResponse<PatientSummary>>(`/patients?${listQuery(params)}`),
    placeholderData: keepPreviousData,
  })
}

export function usePatient(id: string) {
  return useQuery({ queryKey: patientKeys.detail(id), queryFn: () => apiRequest<Patient>(`/patients/${id}`) })
}

/**
 * Stores the returned patient and refreshes the lists. A failure (e.g. CONCURRENT_MODIFICATION) refreshes
 * everything so the next attempt uses current data.
 */
function usePatientMutation<V>(request: (variables: V) => Promise<Patient>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (patient) => {
      queryClient.setQueryData(patientKeys.detail(patient.id), patient)
      void queryClient.invalidateQueries({ queryKey: [...patientKeys.all, 'list'] })
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: patientKeys.all }),
  })
}

export function useCreatePatient() {
  return usePatientMutation((request: CreatePatientRequest) =>
    apiRequest<Patient>('/patients', { method: 'POST', body: request }),
  )
}

export function useUpdatePatient(id: string) {
  return usePatientMutation((request: UpdatePatientRequest) =>
    apiRequest<Patient>(`/patients/${id}`, { method: 'PUT', body: request }),
  )
}

export function useChangePatientStatus(id: string) {
  return usePatientMutation((status: PatientStatus) =>
    apiRequest<Patient>(`/patients/${id}/status`, { method: 'PATCH', body: { status } }),
  )
}

export function useChangePrimaryDoctor(id: string) {
  return usePatientMutation((doctorId: string) =>
    apiRequest<Patient>(`/patients/${id}/primary-doctor`, { method: 'PATCH', body: { doctorId } }),
  )
}

export function useAddAuthorizedDoctor(id: string) {
  return usePatientMutation((doctorId: string) =>
    apiRequest<Patient>(`/patients/${id}/doctors`, { method: 'POST', body: { doctorId } }),
  )
}

/** Returns no body, so the patient is reloaded. */
export function useRemoveDoctor(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (doctorId: string) => apiRequest<void>(`/patients/${id}/doctors/${doctorId}`, { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: patientKeys.detail(id) }),
  })
}

export function patientName(patient: Pick<PatientSummary, 'firstName' | 'lastName'>): string {
  return `${patient.firstName} ${patient.lastName}`
}

/** Whole years between a "YYYY-MM-DD" birth date and today ("YYYY-MM-DD", clinic-local). */
export function ageOn(dateOfBirth: string, today: string): number {
  const [birthYear, birthRest] = [Number(dateOfBirth.slice(0, 4)), dateOfBirth.slice(5)]
  const [year, rest] = [Number(today.slice(0, 4)), today.slice(5)]
  return year - birthYear - (rest < birthRest ? 1 : 0)
}
