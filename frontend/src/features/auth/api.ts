import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { isApiError } from '../../shared/api/ApiError'
import { apiRequest } from '../../shared/api/http'
import type { Language } from '../../i18n/languages'

export type Role = 'OWNER' | 'DOCTOR' | 'SECRETARY'

/** GET /api/v1/auth/me (api.md §5). */
export interface CurrentUser {
  id: string
  firstName: string
  lastName: string
  email: string
  role: Role
  preferredLanguage: Language | null
  doctorId: string | null
  clinic: {
    id: string
    name: string
    timeZone: string | null
    defaultLanguage: Language | null
  }
}

export interface TokenPasswordRequest {
  token: string
  newPassword: string
  confirmPassword: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export const currentUserKey = ['auth', 'me'] as const

/** The signed-in user, or null when there is no valid session. */
async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    return await apiRequest<CurrentUser>('/auth/me')
  } catch (error) {
    if (isApiError(error) && error.status === 401) {
      return null
    }
    throw error
  }
}

export function useCurrentUser() {
  return useQuery({ queryKey: currentUserKey, queryFn: fetchCurrentUser, staleTime: 5 * 60_000 })
}

/**
 * Starts a new session in the cache: no data from the previous account may survive. The session query is updated
 * in place rather than removed, so the route guards observing it react in the same render as everything else.
 */
function replaceSession(queryClient: QueryClient, user: CurrentUser | null) {
  queryClient.setQueryData(currentUserKey, user)
  queryClient.removeQueries({ predicate: (query) => query.queryHash !== JSON.stringify(currentUserKey) })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: { email: string; password: string }) =>
      apiRequest<CurrentUser>('/auth/login', { method: 'POST', body: credentials }),
    onSuccess: (user) => replaceSession(queryClient, user),
  })
}

const signedOutKey = ['auth', 'signedOut'] as const

/**
 * Ends the session. The route guard then sends the user to sign-in; the sign-out marker tells it not to remember
 * the current page, so whoever signs in next starts from their own home page. Signing in clears the marker.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiRequest<void>('/auth/logout', { method: 'POST' }),
    onSettled: () => {
      replaceSession(queryClient, null)
      queryClient.setQueryData(signedOutKey, true)
    },
  })
}

/** Whether the session ended by signing out, rather than by expiring. */
export function useSignedOut(): boolean {
  return useQueryClient().getQueryData<boolean>(signedOutKey) === true
}

export function useUpdatePreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (preferences: { preferredLanguage: Language | null }) =>
      apiRequest<CurrentUser>('/auth/me/preferences', { method: 'PUT', body: preferences }),
    onSuccess: (user) => queryClient.setQueryData(currentUserKey, user),
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (request: ChangePasswordRequest) =>
      apiRequest<void>('/auth/change-password', { method: 'POST', body: request }),
  })
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => apiRequest<void>('/auth/forgot-password', { method: 'POST', body: { email } }),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (request: TokenPasswordRequest) =>
      apiRequest<void>('/auth/reset-password', { method: 'POST', body: request }),
  })
}

export function useAcceptInvitation() {
  return useMutation({
    mutationFn: (request: TokenPasswordRequest) =>
      apiRequest<void>('/auth/accept-invitation', { method: 'POST', body: request }),
  })
}
