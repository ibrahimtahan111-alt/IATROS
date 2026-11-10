import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { fakeApi, json, noContent, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'

const secretary = testUser('SECRETARY')

function systemPrefersDark(dark: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: dark && query === '(prefers-color-scheme: dark)',
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))
}

describe('account', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    systemPrefersDark(false)
  })

  afterEach(async () => {
    await i18n.changeLanguage('en')
    delete document.documentElement.dataset.theme
  })

  it('shows the profile of the signed-in user', async () => {
    fakeApi({ 'GET /auth/me': () => json(secretary) })
    renderRoute('/account')

    const profile = within(await screen.findByRole('region', { name: 'Profile' }))
    expect(profile.getByText('Elena Martin')).toBeInTheDocument()
    expect(profile.getByText('elena@demo.iatros.test')).toBeInTheDocument()
    expect(profile.getByText('Secretary')).toBeInTheDocument()
    expect(profile.getByText('Demo Clinic')).toBeInTheDocument()
  })

  it('switches the language at once and saves it to the account', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(secretary),
      'PUT /auth/me/preferences': () => json({ ...secretary, preferredLanguage: 'fr' }),
    })
    renderRoute('/account')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('combobox', { name: 'Language' }))
    await user.click(await screen.findByRole('option', { name: 'Français' }))

    expect(await screen.findByRole('heading', { name: 'Mon compte' })).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('fr')
    await waitFor(() =>
      expect(requests.find((request) => request.path === '/auth/me/preferences')?.body).toEqual({
        preferredLanguage: 'fr',
      }),
    )
  })

  it('switches the theme on this device only, and follows the system when asked', async () => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(secretary) })
    renderRoute('/account')
    const user = userEvent.setup()

    const theme = await screen.findByRole('combobox', { name: 'Theme' })
    expect(theme).toHaveTextContent('Match system')

    await user.click(theme)
    await user.click(await screen.findByRole('option', { name: 'Dark' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('iatros.theme')).toBe('dark')

    systemPrefersDark(false)
    await user.click(theme)
    await user.click(await screen.findByRole('option', { name: 'Match system' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('iatros.theme')).toBeNull()
    expect(requests.some((request) => request.method !== 'GET')).toBe(false)
  })

  it('changes the password and clears the form', async () => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(secretary), 'POST /auth/change-password': noContent })
    renderRoute('/account')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Current password'), 'correct-horse-battery')
    await user.type(screen.getByLabelText('New password'), 'a-brand-new-passphrase')
    await user.type(screen.getByLabelText('Confirm new password'), 'a-brand-new-passphrase')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Your password has been changed.')).toBeInTheDocument()
    expect(requests.find((request) => request.path === '/auth/change-password')?.body).toEqual({
      currentPassword: 'correct-horse-battery',
      newPassword: 'a-brand-new-passphrase',
      confirmPassword: 'a-brand-new-passphrase',
    })
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  })

  it('checks the new password before submitting and shows a wrong current password next to its field', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(secretary),
      'POST /auth/change-password': () =>
        problem(400, 'VALIDATION_ERROR', { fieldErrors: [{ field: 'currentPassword', code: 'INCORRECT_PASSWORD' }] }),
    })
    renderRoute('/account')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Current password'), 'guess')
    await user.type(screen.getByLabelText('New password'), 'short')
    await user.type(screen.getByLabelText('Confirm new password'), 'other')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('Use at least 8 characters.')).toBeInTheDocument()
    expect(requests.some((request) => request.path === '/auth/change-password')).toBe(false)

    await user.clear(screen.getByLabelText('New password'))
    await user.type(screen.getByLabelText('New password'), 'a-brand-new-passphrase')
    await user.clear(screen.getByLabelText('Confirm new password'))
    await user.type(screen.getByLabelText('Confirm new password'), 'a-brand-new-passphrase')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('The current password is incorrect.')).toBeInTheDocument()
    expect(screen.getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('forgot password page', () => {
  it('confirms the same way whatever the address, without revealing accounts', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    const { requests } = fakeApi({
      'GET /auth/me': () => problem(401, 'UNAUTHENTICATED'),
      'POST /auth/forgot-password': () => new Response(null, { status: 202 }),
    })
    renderRoute('/forgot-password')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'someone@example.test')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByText('Check your inbox')).toBeInTheDocument()
    expect(requests.find((request) => request.path === '/auth/forgot-password')?.body).toEqual({
      email: 'someone@example.test',
    })
    expect(screen.getByRole('link', { name: 'Back to sign in' })).toHaveAttribute('href', '/login')
  })

  it('validates the address and reports rate limiting', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    fakeApi({
      'GET /auth/me': () => problem(401, 'UNAUTHENTICATED'),
      'POST /auth/forgot-password': () => problem(429, 'RATE_LIMITED', {}, { 'Retry-After': '60' }),
    })
    renderRoute('/forgot-password')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Send reset link' }))
    expect(await screen.findByText('This field is required.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Email'), 'someone@example.test')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts')
  })
})
