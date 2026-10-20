import { describe, expect, it } from 'vitest'
import { fakeApi, json, noContent, problem } from '../../test/fakeApi'
import { ApiError } from './ApiError'
import { apiRequest } from './http'

function setCsrfCookie(value: string) {
  document.cookie = `XSRF-TOKEN=${value}; path=/`
}

describe('apiRequest', () => {
  it('sends no CSRF header on reads', async () => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json({ id: '1' }) })

    await expect(apiRequest('/auth/me')).resolves.toEqual({ id: '1' })
    expect(requests[0]?.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('echoes the XSRF-TOKEN cookie on state-changing requests', async () => {
    setCsrfCookie('token-from-cookie')
    const { requests } = fakeApi({ 'POST /auth/logout': noContent })

    await apiRequest('/auth/logout', { method: 'POST' })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.headers['X-XSRF-TOKEN']).toBe('token-from-cookie')
  })

  it('fetches a CSRF token first when the cookie is missing', async () => {
    const { requests } = fakeApi({
      'GET /auth/csrf': () => {
        setCsrfCookie('fresh-token')
        return noContent()
      },
      'POST /auth/forgot-password': () => new Response(null, { status: 202 }),
    })

    await apiRequest('/auth/forgot-password', { method: 'POST', body: { email: 'a@b.test' } })

    expect(requests.map((request) => request.path)).toEqual(['/auth/csrf', '/auth/forgot-password'])
    expect(requests[1]?.headers['X-XSRF-TOKEN']).toBe('fresh-token')
    expect(requests[1]?.body).toEqual({ email: 'a@b.test' })
  })

  it('refreshes a rejected CSRF token and retries once', async () => {
    setCsrfCookie('stale')
    let attempts = 0
    const { requests } = fakeApi({
      'GET /auth/csrf': () => {
        setCsrfCookie('rotated')
        return noContent()
      },
      'POST /auth/change-password': () => {
        attempts += 1
        return attempts === 1 ? problem(403, 'CSRF_TOKEN_INVALID') : noContent()
      },
    })

    await apiRequest('/auth/change-password', { method: 'POST', body: {} })

    expect(attempts).toBe(2)
    expect(requests.at(-1)?.headers['X-XSRF-TOKEN']).toBe('rotated')
  })

  it('does not retry other 403 responses', async () => {
    setCsrfCookie('token')
    const { requests } = fakeApi({ 'POST /staff': () => problem(403, 'FORBIDDEN') })

    await expect(apiRequest('/staff', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
    expect(requests).toHaveLength(1)
  })

  it('turns problem documents into ApiError with code, field errors, and Retry-After', async () => {
    setCsrfCookie('token')
    fakeApi({
      'POST /auth/login': () =>
        problem(429, 'RATE_LIMITED', { fieldErrors: [{ field: 'email', code: 'REQUIRED' }] }, { 'Retry-After': '120' }),
    })

    const error = await apiRequest('/auth/login', { method: 'POST', body: {} }).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 429, code: 'RATE_LIMITED', retryAfterSeconds: 120 })
    expect((error as ApiError).fieldError('email')).toBe('REQUIRED')
  })

  it('reports unreachable servers as NETWORK_ERROR', async () => {
    fakeApi({ 'GET /auth/me': () => Promise.reject(new TypeError('Failed to fetch')) })

    await expect(apiRequest('/auth/me')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('keeps the HTTP status when the error body is not a problem document', async () => {
    fakeApi({ 'GET /patients': () => new Response('<html>Bad gateway</html>', { status: 502 }) })

    await expect(apiRequest('/patients')).rejects.toMatchObject({ status: 502, code: 'HTTP_502' })
  })
})
