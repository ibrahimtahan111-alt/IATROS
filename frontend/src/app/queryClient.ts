import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { isApiError } from '../shared/api/ApiError'
import { currentUserKey } from '../features/auth/api'

/** Client errors are deterministic; retrying them only delays feedback. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (isApiError(error) && error.status >= 400 && error.status < 500) {
    return false
  }
  return failureCount < 1
}

export function createQueryClient(): QueryClient {
  // The session expired or was revoked: route guards send the user back to the login page.
  const onError = (error: unknown) => {
    if (isApiError(error) && error.status === 401 && error.code === 'UNAUTHENTICATED') {
      queryClient.setQueryData(currentUserKey, null)
    }
  }
  const queryClient: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: shouldRetry,
        refetchOnWindowFocus: true,
      },
      mutations: {
        retry: false,
      },
    },
  })
  return queryClient
}
