import { vi } from 'vitest'
import type { CurrentUser, Role } from '../features/auth/api'

export interface RecordedRequest {
  method: string
  /** Without the query string. */
  path: string
  query: URLSearchParams
  headers: Record<string, string>
  body: unknown
}

type Handler = (request: RecordedRequest) => Response | Promise<Response>

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}, headers: Record<string, string> = {}) {
  return json({ status, code, ...extra }, status, { 'Content-Type': 'application/problem+json', ...headers })
}

export const noContent = () => new Response(null, { status: 204 })

/**
 * Replaces fetch with handlers keyed by "METHOD /path" (path relative to /api/v1, query string excluded). Unhandled
 * requests fail the test loudly instead of hanging.
 */
export function fakeApi(handlers: Record<string, Handler>) {
  const requests: RecordedRequest[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input.toString(), 'http://localhost')
    const request: RecordedRequest = {
      method: init?.method ?? 'GET',
      path: url.pathname.replace(/^\/api\/v1/, ''),
      query: url.searchParams,
      headers: (init?.headers as Record<string, string> | undefined) ?? {},
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body instanceof FormData ? init.body : undefined,
    }
    requests.push(request)
    const handler = handlers[`${request.method} ${request.path}`]
    if (!handler) {
      throw new Error(`Unhandled request: ${request.method} ${request.path}`)
    }
    return handler(request)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { requests, fetchMock }
}

export function testUser(role: Role, overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: '0190a000-0000-7000-8000-000000000001',
    firstName: 'Elena',
    lastName: 'Martin',
    email: 'elena@demo.iatros.test',
    role,
    preferredLanguage: null,
    doctorId: role === 'SECRETARY' ? null : '0190a000-0000-7000-8000-000000000002',
    clinic: { id: '0190a000-0000-7000-8000-000000000003', name: 'Demo Clinic', timeZone: 'Europe/Athens', defaultLanguage: 'en' },
    ...overrides,
  }
}
