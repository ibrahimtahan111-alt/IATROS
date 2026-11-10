import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/http'
import type { PageResponse } from '../../shared/api/types'
import { listQuery, type ListParams } from '../../shared/hooks/useListSearchParams'
import type { Role } from '../auth/api'

export type StaffStatus = 'INVITED' | 'ACTIVE' | 'DEACTIVATED'
export type StaffRole = Exclude<Role, 'OWNER'>
export type StaffFilter = 'role' | 'status'

/** api.md §7. */
export interface StaffMember {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  role: Role
  status: StaffStatus
  lastLoginAt: string | null
  createdAt: string
  version: number
  doctor: {
    id: string
    primarySpecialty: string
    additionalSpecialty: string | null
    registrationNumber: string | null
  } | null
}

export interface CreateStaffRequest {
  firstName: string
  lastName: string
  email: string
  phone: string | null
  role: StaffRole
  doctor: {
    primarySpecialty: string
    additionalSpecialty: string | null
    registrationNumber: string | null
  } | null
}

export interface UpdateStaffRequest {
  firstName: string
  lastName: string
  email: string
  phone: string | null
  version: number
}

const staffKeys = {
  all: ['staff'] as const,
  list: (params: ListParams<StaffFilter>) => ['staff', 'list', params] as const,
  detail: (id: string) => ['staff', 'detail', id] as const,
}

export function useStaffList(params: ListParams<StaffFilter>) {
  return useQuery({
    queryKey: staffKeys.list(params),
    queryFn: () => apiRequest<PageResponse<StaffMember>>(`/staff?${listQuery(params)}`),
    placeholderData: keepPreviousData,
  })
}

export function useStaffMember(id: string) {
  return useQuery({ queryKey: staffKeys.detail(id), queryFn: () => apiRequest<StaffMember>(`/staff/${id}`) })
}

/**
 * Stores the returned member and refreshes every list, since sorting and filters may now place it differently.
 * A failure (e.g. CONCURRENT_MODIFICATION) refreshes everything so the next attempt uses current data.
 */
function useStaffMutation<V>(request: (variables: V) => Promise<StaffMember>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (member) => {
      queryClient.setQueryData(staffKeys.detail(member.id), member)
      void queryClient.invalidateQueries({ queryKey: [...staffKeys.all, 'list'] })
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: staffKeys.all }),
  })
}

export function useCreateStaffMember() {
  return useStaffMutation((request: CreateStaffRequest) =>
    apiRequest<StaffMember>('/staff', { method: 'POST', body: request }),
  )
}

export function useUpdateStaffMember(id: string) {
  return useStaffMutation((request: UpdateStaffRequest) =>
    apiRequest<StaffMember>(`/staff/${id}`, { method: 'PUT', body: request }),
  )
}

export function useChangeStaffStatus() {
  return useStaffMutation(({ id, status }: { id: string; status: Exclude<StaffStatus, 'INVITED'> }) =>
    apiRequest<StaffMember>(`/staff/${id}/status`, { method: 'PATCH', body: { status } }),
  )
}

export function useResendInvitation() {
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/staff/${id}/invitation`, { method: 'POST' }),
  })
}

export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/staff/${id}/password-reset`, { method: 'POST' }),
  })
}

export function fullName(member: Pick<StaffMember, 'firstName' | 'lastName'>): string {
  return `${member.firstName} ${member.lastName}`
}
