export interface FieldError {
  field: string
  code: string
}

/** A failed API call, carrying the backend's machine-readable error code (api.md §31). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: readonly FieldError[]
  readonly retryAfterSeconds: number | null

  constructor(status: number, code: string, fieldErrors: readonly FieldError[] = [], retryAfterSeconds: number | null = null) {
    super(`API error ${status} ${code}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.retryAfterSeconds = retryAfterSeconds
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.code
  }
}

/** Network failure or unreadable response: the request may not have reached the server. */
export const NETWORK_ERROR = 'NETWORK_ERROR'

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
