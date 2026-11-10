import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { fakeApi, json, noContent, problem, testUser } from '../test/fakeApi'
import { renderRoute } from '../test/renderRoute'

const signedOut = { 'GET /auth/me': () => problem(401, 'UNAUTHENTICATED') }

function signedInAs(role: Parameters<typeof testUser>[0], overrides: Parameters<typeof testUser>[1] = {}) {
  return { 'GET /auth/me': () => json(testUser(role, overrides)) }
}

describe('routing and route protection', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en')
  })

  it('sends signed-out users to the sign-in page', async () => {
    fakeApi(signedOut)
    const { router } = renderRoute('/patients')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('returns to the requested page after signing in', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    let signedIn = false
    const { requests } = fakeApi({
      'GET /auth/me': () => (signedIn ? json(testUser('OWNER')) : problem(401, 'UNAUTHENTICATED')),
      'POST /auth/login': () => {
        signedIn = true
        return json(testUser('OWNER'))
      },
    })
    const { router } = renderRoute('/patients')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'elena@demo.iatros.test')
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/patients'))
    expect(requests.find((request) => request.path === '/auth/login')?.body).toEqual({
      email: 'elena@demo.iatros.test',
      password: 'correct-horse-battery',
    })
  })

  it('shows a translated message when sign-in is rejected', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    fakeApi({ ...signedOut, 'POST /auth/login': () => problem(401, 'INVALID_CREDENTIALS') })
    renderRoute('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Email'), 'elena@demo.iatros.test')
    await user.type(screen.getByLabelText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect')
  })

  it('validates the sign-in form before calling the API', async () => {
    const { requests } = fakeApi(signedOut)
    renderRoute('/login')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findAllByText('This field is required.')).toHaveLength(2)
    expect(screen.getByLabelText('Email')).toHaveFocus()
    expect(requests.some((request) => request.path === '/auth/login')).toBe(false)

    await user.type(screen.getByLabelText('Email'), 'elena@demo.iatros.test')
    expect(screen.getAllByText('This field is required.')).toHaveLength(1)
    expect(screen.getByLabelText('Email')).not.toHaveAttribute('aria-invalid')
  })

  it('lands each role on its home page', async () => {
    fakeApi(signedInAs('SECRETARY'))
    const { router } = renderRoute('/')

    await waitFor(() => expect(router.state.location.pathname).toBe('/calendar'))
  })

  it('shows only the modules of the role in the navigation', async () => {
    fakeApi(signedInAs('DOCTOR'))
    renderRoute('/dashboard')

    const nav = await screen.findByRole('navigation', { name: 'Main navigation' })
    const links = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent)
    expect(links).toEqual(['My day', 'Calendar', 'Patients', 'My schedule'])
  })

  it('does not render modules outside the role, even by URL', async () => {
    fakeApi(signedInAs('SECRETARY'))
    renderRoute('/team')

    expect(await screen.findByRole('heading', { name: 'This page is not available' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Team' })).not.toBeInTheDocument()
  })

  it('keeps signed-in users away from the sign-in page', async () => {
    fakeApi(signedInAs('OWNER'))
    const { router } = renderRoute('/login')

    await waitFor(() => expect(router.state.location.pathname).toBe('/dashboard'))
  })

  it("applies the account's saved language after sign-in", async () => {
    fakeApi(signedInAs('OWNER', { preferredLanguage: 'fr' }))
    renderRoute('/dashboard')

    expect(
      await screen.findByRole('heading', { name: /^(Bonjour|Bon après-midi|Bonsoir), Elena$/ }),
    ).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('fr')
  })

  it('returns to sign-in when the session ends, without sending the next user back to this page', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    let signedIn = true
    fakeApi({
      'GET /auth/me': () => (signedIn ? json(testUser('OWNER')) : problem(401, 'UNAUTHENTICATED')),
      'POST /auth/logout': () => {
        signedIn = false
        return noContent()
      },
    })
    const { router } = renderRoute('/patients?q=martin')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Account menu for Elena Martin' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.state).toBeNull()
  })

  it('renders a translated not-found page for unknown routes', async () => {
    await i18n.changeLanguage('fr')
    renderRoute('/does-not-exist')

    expect(await screen.findByRole('heading', { name: 'Page introuvable' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Retour à l’accueil' })).toHaveAttribute('href', '/')
  })
})
