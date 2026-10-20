import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { fakeApi, noContent, problem } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import { safeRedirectPath } from './session'

const TOKEN = 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde'

describe('reset password page', () => {
  afterEach(() => {
    window.history.replaceState(null, '', '/')
  })

  it('submits the token from the link fragment and removes it from the address bar', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    window.history.replaceState(null, '', `/reset-password#token=${TOKEN}`)
    const { requests } = fakeApi({ 'POST /auth/reset-password': noContent })
    renderRoute('/reset-password')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('New password'), 'a-brand-new-passphrase')
    await user.type(screen.getByLabelText('Confirm new password'), 'a-brand-new-passphrase')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByText('Password changed')).toBeInTheDocument()
    expect(requests.find((request) => request.path === '/auth/reset-password')?.body).toEqual({
      token: TOKEN,
      newPassword: 'a-brand-new-passphrase',
      confirmPassword: 'a-brand-new-passphrase',
    })
    expect(window.location.hash).toBe('')
  })

  it('checks length and confirmation before submitting', async () => {
    window.history.replaceState(null, '', `/reset-password#token=${TOKEN}`)
    const { requests } = fakeApi({})
    renderRoute('/reset-password')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('New password'), 'short')
    await user.type(screen.getByLabelText('Confirm new password'), 'different')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByText('Use at least 12 characters.')).toBeInTheDocument()
    expect(requests).toHaveLength(0)
  })

  it('explains an expired or used link and offers a new one', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    window.history.replaceState(null, '', `/reset-password#token=${TOKEN}`)
    fakeApi({ 'POST /auth/reset-password': () => problem(400, 'INVALID_TOKEN') })
    renderRoute('/reset-password')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('New password'), 'a-brand-new-passphrase')
    await user.type(screen.getByLabelText('Confirm new password'), 'a-brand-new-passphrase')
    await user.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByText('This link can no longer be used')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/forgot-password')
  })

  it('shows the invalid-link state when there is no token', async () => {
    fakeApi({})
    renderRoute('/reset-password')

    expect(await screen.findByText('This link can no longer be used')).toBeInTheDocument()
  })
})

describe('safeRedirectPath', () => {
  it('accepts in-app paths only', () => {
    expect(safeRedirectPath('/patients?search=martin')).toBe('/patients?search=martin')
    expect(safeRedirectPath('//evil.example')).toBeNull()
    expect(safeRedirectPath('https://evil.example')).toBeNull()
    expect(safeRedirectPath(undefined)).toBeNull()
  })
})
