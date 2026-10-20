import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'

export type AllergySeverity = 'MILD' | 'MODERATE' | 'SEVERE' | 'UNKNOWN'
export type ConditionStatus = 'ACTIVE' | 'RESOLVED'

export const SEVERITIES: readonly AllergySeverity[] = ['MILD', 'MODERATE', 'SEVERE', 'UNKNOWN']
export const CONDITION_STATUSES: readonly ConditionStatus[] = ['ACTIVE', 'RESOLVED']

/** Structured sections of GET /patients/{id}/medical-record (api.md §12). */
export interface Allergy {
  id: string
  allergen: string
  reaction: string | null
  severity: AllergySeverity | null
  notes: string | null
  updatedAt: string
  version: number
}

export interface Condition {
  id: string
  name: string
  description: string | null
  status: ConditionStatus
  diagnosedOn: string | null
  updatedAt: string
  version: number
}

export interface Medication {
  id: string
  name: string
  dosage: string | null
  frequency: string | null
  notes: string | null
  startDate: string | null
  endDate: string | null
  active: boolean
  updatedAt: string
  version: number
}

export interface HistoryEntry {
  id: string
  description: string
  occurredOn: string | null
  notes: string | null
  updatedAt: string
  version: number
}

export interface MedicalRecord {
  patientId: string
  allergies: Allergy[]
  conditions: Condition[]
  medications: Medication[]
  history: HistoryEntry[]
}

export interface PersonRef {
  id: string
  firstName: string
  lastName: string
}

export interface MedicalNote {
  id: string
  content: string
  author: PersonRef | null
  createdAt: string
  updatedAt: string
  edited: boolean
  /** Only the author may edit or remove a note (D-037). */
  editable: boolean
  version: number
}

export interface AllergyRequest {
  allergen: string
  reaction: string | null
  severity: AllergySeverity | null
  notes: string | null
}

export interface ConditionRequest {
  name: string
  description: string | null
  status: ConditionStatus
  diagnosedOn: string | null
}

export interface MedicationRequest {
  name: string
  dosage: string | null
  frequency: string | null
  notes: string | null
  startDate: string | null
  endDate: string | null
  active: boolean
}

export interface HistoryEntryRequest {
  description: string
  occurredOn: string | null
  notes: string | null
}

/** URL segment of each structured section. */
export interface SectionRequests {
  allergies: AllergyRequest
  conditions: ConditionRequest
  medications: MedicationRequest
  history: HistoryEntryRequest
}

export type RecordSection = keyof SectionRequests

const recordKeys = {
  record: (patientId: string) => ['medical-record', patientId] as const,
  notes: (patientId: string) => ['medical-record', patientId, 'notes'] as const,
  notesPage: (patientId: string, page: number) => ['medical-record', patientId, 'notes', page] as const,
}

const recordPath = (patientId: string) => `/patients/${patientId}/medical-record`

export function useMedicalRecord(patientId: string) {
  return useQuery({
    queryKey: recordKeys.record(patientId),
    queryFn: () => apiRequest<MedicalRecord>(recordPath(patientId)),
  })
}

/**
 * Adds an entry, or updates it when `id` is given; updates carry the version the form was loaded from (D-043).
 * Any outcome reloads the record, so a conflict leaves the latest data on screen.
 */
export function useSaveEntry<S extends RecordSection>(patientId: string, section: S) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, request }: { id?: string; version?: number; request: SectionRequests[S] }) =>
      id
        ? apiRequest<unknown>(`${recordPath(patientId)}/${section}/${id}`, { method: 'PUT', body: { ...request, version } })
        : apiRequest<unknown>(`${recordPath(patientId)}/${section}`, { method: 'POST', body: request }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: recordKeys.record(patientId), exact: true }),
  })
}

export function useRemoveEntry(patientId: string, section: RecordSection) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`${recordPath(patientId)}/${section}/${id}`, { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: recordKeys.record(patientId), exact: true }),
  })
}

export const NOTES_PAGE_SIZE = 10

export function useMedicalNotes(patientId: string, page: number) {
  return useQuery({
    queryKey: recordKeys.notesPage(patientId, page),
    queryFn: () =>
      apiRequest<PageResponse<MedicalNote>>(`${recordPath(patientId)}/notes?page=${page}&size=${NOTES_PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  })
}

export function useSaveNote(patientId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, content }: { id?: string; version?: number; content: string }) =>
      id
        ? apiRequest<MedicalNote>(`${recordPath(patientId)}/notes/${id}`, { method: 'PUT', body: { content, version } })
        : apiRequest<MedicalNote>(`${recordPath(patientId)}/notes`, { method: 'POST', body: { content } }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: recordKeys.notes(patientId) }),
  })
}

export function useRemoveNote(patientId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`${recordPath(patientId)}/notes/${id}`, { method: 'DELETE' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: recordKeys.notes(patientId) }),
  })
}

export function personName(person: PersonRef): string {
  return `${person.firstName} ${person.lastName}`
}
