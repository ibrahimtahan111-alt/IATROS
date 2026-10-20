import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi, json, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { Clinic } from './api'

const clinic: Clinic = {
  id: '0190a000-0000-7000-8000-000000000003',
  name: 'Demo Clinic',
  email: null,
  phone: null,
  address: null,
  version: 2,
  settings: { timeZone: 'Europe/Athens', defaultAppointmentMinutes: 30, defaultLanguage: 'en', weekStartDay: 1, version: 5 },
}

const owner = { 'GET /auth/me': () => json(testUser('OWNER')) }

function section(name: string) {
  return screen.getByRole('region', { name })
}

describe('clinic settings', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('saves the clinic information with its version and refreshes the session', async () => {
    const { requests } = fakeApi({
      ...owner,
      'GET /clinic': () => json(clinic),
      'PUT /clinic': () => json({ ...clinic, name: 'Cabinet Martin', address: '12 Rue de la Paix', version: 3 }),
    })
    renderRoute('/settings')
    const user = userEvent.setup()

    const profile = within(await screen.findByRole('region', { name: 'Clinic information' }))
    const save = profile.getByRole('button', { name: 'Save changes' })
    expect(save).toBeDisabled()

    await user.clear(profile.getByLabelText('Clinic name'))
    await user.type(profile.getByLabelText('Clinic name'), 'Cabinet Martin')
    await user.type(profile.getByLabelText(/Address/), '12 Rue de la Paix')
    await user.click(save)

    expect(await screen.findByText('Clinic information saved.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PUT')?.body).toEqual({
      name: 'Cabinet Martin',
      email: null,
      phone: null,
      address: '12 Rue de la Paix',
      version: 2,
    })
    await waitFor(() => expect(requests.filter((request) => request.path === '/auth/me').length).toBeGreaterThan(1))
    expect(save).toBeDisabled()
  })

  it('validates the clinic name and contact email', async () => {
    const { requests } = fakeApi({ ...owner, 'GET /clinic': () => json(clinic) })
    renderRoute('/settings')
    const user = userEvent.setup()

    const profile = within(await screen.findByRole('region', { name: 'Clinic information' }))
    await user.clear(profile.getByLabelText('Clinic name'))
    await user.type(profile.getByLabelText(/Contact email/), 'not-an-email')
    await user.click(profile.getByRole('button', { name: 'Save changes' }))

    expect(await profile.findByText('This field is required.')).toBeInTheDocument()
    expect(profile.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'PUT')).toBe(false)
  })

  it('updates scheduling defaults', async () => {
    const { requests } = fakeApi({
      ...owner,
      'GET /clinic': () => json(clinic),
      'PUT /clinic/settings': () =>
        json({ ...clinic, settings: { ...clinic.settings, defaultAppointmentMinutes: 20, version: 6 } }),
    })
    renderRoute('/settings')
    const user = userEvent.setup()

    await screen.findByRole('region', { name: 'Scheduling and region' })
    const settings = within(section('Scheduling and region'))
    await user.click(settings.getByRole('combobox', { name: 'Default appointment length' }))
    await user.click(await screen.findByRole('option', { name: '20 minutes' }))
    await user.click(settings.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Settings saved.')).toBeInTheDocument()
    expect(requests.find((request) => request.path === '/clinic/settings')?.body).toEqual({
      timeZone: 'Europe/Athens',
      defaultAppointmentMinutes: 20,
      defaultLanguage: 'en',
      weekStartDay: 1,
      version: 5,
    })
  })

  it('explains a concurrent change instead of overwriting it', async () => {
    fakeApi({
      ...owner,
      'GET /clinic': () => json(clinic),
      'PUT /clinic': () => problem(409, 'CONCURRENT_MODIFICATION'),
    })
    renderRoute('/settings')
    const user = userEvent.setup()

    const profile = within(await screen.findByRole('region', { name: 'Clinic information' }))
    await user.type(profile.getByLabelText(/Phone/), '+30 210 000 0000')
    await user.click(profile.getByRole('button', { name: 'Save changes' }))

    expect(await profile.findByRole('alert')).toHaveTextContent('Someone else changed this item')
  })

  it.each(['DOCTOR', 'SECRETARY'] as const)('is not available to the %s role', async (role) => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(testUser(role)) })
    renderRoute('/settings')

    expect(await screen.findByRole('heading', { name: 'This page is not available' })).toBeInTheDocument()
    expect(requests.some((request) => request.path === '/clinic')).toBe(false)
  })
})
