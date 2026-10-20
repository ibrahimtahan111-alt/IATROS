import { useCurrentUser, type CurrentUser } from './api'

/** The signed-in user inside routes protected by RequireAuth. */
export function useSessionUser(): CurrentUser {
  const { data } = useCurrentUser()
  if (!data) {
    throw new Error('useSessionUser must be used inside RequireAuth')
  }
  return data
}

/** Only same-app paths are accepted as post-login destinations, never external URLs. */
export function safeRedirectPath(candidate: unknown): string | null {
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return null
  }
  return candidate
}
