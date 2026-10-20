import { ApiError, NETWORK_ERROR, type FieldError } from './ApiError'

const API_BASE = '/api/v1'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  method?: Method
  body?: unknown
  signal?: AbortSignal
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  const match = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return match ? decodeURIComponent(match.slice(prefix.length)) : null
}

/** Obtains the XSRF-TOKEN cookie (decisions.md D-011, D-017). */
async function refreshCsrfToken(): Promise<string | null> {
  await fetch(`${API_BASE}/auth/csrf`, { credentials: 'same-origin' })
  return readCookie(CSRF_COOKIE)
}

async function csrfToken(): Promise<string | null> {
  return readCookie(CSRF_COOKIE) ?? refreshCsrfToken()
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = Number(response.headers.get('Retry-After'))
  let code = `HTTP_${response.status}`
  let fieldErrors: FieldError[] = []
  try {
    const problem = (await response.json()) as { code?: unknown; fieldErrors?: unknown }
    if (typeof problem.code === 'string') {
      code = problem.code
    }
    if (Array.isArray(problem.fieldErrors)) {
      fieldErrors = problem.fieldErrors as FieldError[]
    }
  } catch {
    // Not a problem document (e.g. a proxy error page); keep the HTTP status code.
  }
  return new ApiError(response.status, code, fieldErrors, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null)
}

async function send(path: string, method: Method, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  // The browser sets the multipart Content-Type (with its boundary) for FormData bodies.
  const multipart = options.body instanceof FormData
  if (options.body !== undefined && !multipart) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers[CSRF_HEADER] = token
  }
  try {
    return await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: options.body === undefined ? undefined : multipart ? (options.body as FormData) : JSON.stringify(options.body),
      credentials: 'same-origin',
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, NETWORK_ERROR)
  }
}

/**
 * Calls the IATROS API. State-changing requests carry the CSRF token; a rejected token (e.g. rotated by a login in
 * another tab) is refreshed and the request retried once. Errors are thrown as {@link ApiError}.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const needsCsrf = !SAFE_METHODS.has(method)

  let response = await send(path, method, options, needsCsrf ? await csrfToken() : null)
  if (needsCsrf && response.status === 403) {
    const error = await toApiError(response.clone())
    if (error.code === 'CSRF_TOKEN_INVALID') {
      response = await send(path, method, options, await refreshCsrfToken())
    }
  }

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204 || response.status === 202 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }
  return (await response.json()) as T
}
