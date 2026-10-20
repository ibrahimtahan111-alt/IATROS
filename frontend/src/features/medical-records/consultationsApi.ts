import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import type { PersonRef } from './api'

/** A consultation in the patient's history (api.md §16). The doctor ref carries the doctor profile ID. */
export interface ConsultationSummary {
  id: string
  consultedAt: string
  doctor: PersonRef
  reason: string
  diagnosis: string | null
  followUpOn: string | null
}

export interface Consultation extends ConsultationSummary {
  patientId: string
  clinicalNotes: string | null
  treatmentNotes: string | null
  followUpNotes: string | null
  appointmentId: string | null
  createdAt: string
  updatedAt: string
  /** Only the consultation's doctor edits it (D-037). */
  editable: boolean
  version: number
}

export interface ConsultationRequest {
  consultedAt: string
  reason: string
  diagnosis: string | null
  clinicalNotes: string | null
  treatmentNotes: string | null
  followUpNotes: string | null
  followUpOn: string | null
}

export const CONSULTATIONS_PAGE_SIZE = 10

const consultationKeys = {
  patient: (patientId: string) => ['consultations', 'patient', patientId] as const,
  page: (patientId: string, page: number) => ['consultations', 'patient', patientId, page] as const,
  detail: (id: string) => ['consultations', 'detail', id] as const,
}

export function useConsultations(patientId: string, page: number) {
  return useQuery({
    queryKey: consultationKeys.page(patientId, page),
    queryFn: () =>
      apiRequest<PageResponse<ConsultationSummary>>(
        `/patients/${patientId}/consultations?page=${page}&size=${CONSULTATIONS_PAGE_SIZE}`,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useConsultation(id: string) {
  return useQuery({
    queryKey: consultationKeys.detail(id),
    queryFn: () => apiRequest<Consultation>(`/consultations/${id}`),
  })
}

function useConsultationMutation<V>(request: (variables: V) => Promise<Consultation>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (consultation) => {
      queryClient.setQueryData(consultationKeys.detail(consultation.id), consultation)
      void queryClient.invalidateQueries({ queryKey: consultationKeys.patient(consultation.patientId) })
      // A linked appointment now shows its consultation.
      void queryClient.invalidateQueries({ queryKey: ['appointments', 'detail'] })
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: ['consultations'] }),
  })
}

/** `appointmentId` links the consultation to the doctor's appointment it records (D-038); set only on creation. */
export function useCreateConsultation(patientId: string) {
  return useConsultationMutation((request: ConsultationRequest & { appointmentId: string | null }) =>
    apiRequest<Consultation>(`/patients/${patientId}/consultations`, { method: 'POST', body: request }),
  )
}

export function useUpdateConsultation(id: string) {
  return useConsultationMutation((request: ConsultationRequest & { version: number }) =>
    apiRequest<Consultation>(`/consultations/${id}`, { method: 'PUT', body: request }),
  )
}
