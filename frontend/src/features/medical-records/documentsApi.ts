import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import type { PersonRef } from './api'

/** Document metadata (api.md §21). The file itself is only served by the content endpoint. */
export interface MedicalDocument {
  id: string
  patientId: string
  consultationId: string | null
  /** Written by the uploader; shown as is, never translated. */
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedBy: PersonRef | null
  uploadedAt: string
  /** The uploader or the owner may archive it (D-044). */
  archivable: boolean
}

export interface DocumentFilters {
  /** Restricts the list to one consultation's attachments. */
  consultationId?: string
  search: string
  page: number
}

export const DOCUMENTS_PAGE_SIZE = 10
/** Mirrors the server limit (D-044); the server stays authoritative. */
export const MAX_DOCUMENT_BYTES = 20 * 1024 * 1024
export const DOCUMENT_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'webp'] as const
export const DOCUMENT_ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp'

const documentKeys = {
  patient: (patientId: string) => ['documents', 'patient', patientId] as const,
  list: (patientId: string, filters: DocumentFilters) => ['documents', 'patient', patientId, filters] as const,
}

export function useDocuments(patientId: string, filters: DocumentFilters) {
  return useQuery({
    queryKey: documentKeys.list(patientId, filters),
    queryFn: () => {
      const params = new URLSearchParams({ page: String(filters.page), size: String(DOCUMENTS_PAGE_SIZE) })
      if (filters.consultationId) params.set('consultationId', filters.consultationId)
      if (filters.search) params.set('search', filters.search)
      return apiRequest<PageResponse<MedicalDocument>>(`/patients/${patientId}/documents?${params}`)
    },
    placeholderData: keepPreviousData,
  })
}

export function useUploadDocument(patientId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, consultationId }: { file: File; consultationId?: string }) => {
      const body = new FormData()
      body.append('file', file)
      if (consultationId) body.append('consultationId', consultationId)
      return apiRequest<MedicalDocument>(`/patients/${patientId}/documents`, { method: 'POST', body })
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: documentKeys.patient(patientId) }),
  })
}

export function useArchiveDocument(patientId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/documents/${id}`, { method: 'DELETE' }),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: documentKeys.patient(patientId) }),
  })
}

/** Same-origin URL of the file; the session cookie authorizes it and every access is audited. */
export function documentContentUrl(id: string, download = false): string {
  return `/api/v1/documents/${id}/content${download ? '?download=true' : ''}`
}

export function documentExtension(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase()
}
