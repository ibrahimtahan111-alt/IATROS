import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi, json, problem, testUser, type RecordedRequest } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { StaffMember } from './api'

const DOCTOR_ID = '0190a000-0000-7000-8000-0000000000d1'

function member(overrides: Partial<StaffMember> = {}): StaffMember {
  return {
    id: DOCTOR_ID,
    firstName: 'Daniel',
    lastName: 'Brown',
    email: 'daniel@demo.iatros.test',
    phone: null,
    role: 'DOCTOR',
    status: 'ACTIVE',
    lastLoginAt: '2026-10-01T08:30:00Z',
    createdAt: '2026-09-01T08:30:00Z',
    version: 3,
    doctor: { id: 'doc-1', primarySpecialty: 'CARDIOLOGY', additionalSpecialty: null, registrationNumber: null },
    ...overrides,
  }
}

const secretary = member({
  id: '0190a000-0000-7000-8000-0000000000s1',
  firstName: 'Sara',
  lastName: 'Adams',
  email: 'sara@demo.iatros.test',
  role: 'SECRETARY',
  status: 'INVITED',
  lastLoginAt: null,
  doctor: null,
})

const page = (content: StaffMember[]) => json({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

const owner = { 'GET /auth/me': () => json(testUser('OWNER')) }
const specialties = {
  'GET /specialties': () =>
    json([
      { id: 's1', code: 'CARDIOLOGY' },
      { id: 's2', code: 'GENERAL_PRACTICE' },
    ]),
}

const lastList = (requests: RecordedRequest[]) => requests.filter((request) => request.path === '/staff').at(-1)

describe('team management', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('lists the team with roles, specialties, and statuses', async () => {
    const { requests } = fakeApi({ ...owner, ...specialties, 'GET /staff': () => page([secretary, member()]) })
    renderRoute('/team')

    const table = await screen.findByRole('table', { name: 'Team members' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(within(rows[0]!).getByRole('link', { name: 'Sara Adams' })).toHaveAttribute('href', `/team/${secretary.id}`)
    expect(within(rows[0]!).getByText('Invitation sent')).toBeInTheDocument()
    expect(within(rows[1]!).getByText('Cardiology')).toBeInTheDocument()
    expect(screen.getByText('2 members')).toBeInTheDocument()
    expect(lastList(requests)?.query.get('sort')).toBe('lastName,asc')
  })

  it('searches and filters on the server and keeps the state in the URL', async () => {
    const { requests } = fakeApi({
      ...owner,
      ...specialties,
      'GET /staff': (request) => page(request.query.get('search') === 'nobody' ? [] : [member()]),
    })
    const { router } = renderRoute('/team')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('combobox', { name: 'Role' }))
    await user.click(await screen.findByRole('option', { name: 'Doctor' }))
    await waitFor(() => expect(lastList(requests)?.query.get('role')).toBe('DOCTOR'))
    expect(router.state.location.search).toContain('role=DOCTOR')
    expect(screen.getByRole('list', { name: 'Active filters' })).toHaveTextContent('Role: Doctor')

    await user.type(screen.getByRole('searchbox', { name: 'Search by name or email' }), 'nobody{Enter}')
    expect(await screen.findByRole('heading', { name: 'No team members match' })).toBeInTheDocument()
    expect(lastList(requests)?.query.get('search')).toBe('nobody')

    await user.click(screen.getByRole('button', { name: 'Clear search and filters' }))
    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(await screen.findByRole('link', { name: 'Daniel Brown' })).toBeInTheDocument()
  })

  it('invites the first member from the empty state', async () => {
    fakeApi({ ...owner, ...specialties, 'GET /staff': () => page([]) })
    renderRoute('/team')

    expect(await screen.findByRole('heading', { name: 'No team members yet' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Add team member' })).toHaveLength(2)
  })

  it('adds a doctor and opens their page', async () => {
    const created = member({ status: 'INVITED', lastLoginAt: null })
    const { requests } = fakeApi({
      ...owner,
      ...specialties,
      'POST /staff': () => json(created, 201),
      [`GET /staff/${DOCTOR_ID}`]: () => json(created),
    })
    const { router } = renderRoute('/team/new')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Add and send invitation' }))
    expect(await screen.findAllByText('This field is required.')).toHaveLength(4)
    expect(screen.getByLabelText('First name')).toHaveFocus()

    await user.type(screen.getByLabelText('First name'), 'Daniel')
    await user.type(screen.getByLabelText('Last name'), 'Brown')
    await user.type(screen.getByLabelText('Email'), 'daniel@demo.iatros.test')
    await user.click(screen.getByRole('radio', { name: /Doctor/ }))
    await user.click(screen.getByRole('button', { name: 'Add and send invitation' }))
    expect(await screen.findByText('This field is required.')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'Primary specialty' }))
    await user.click(await screen.findByRole('option', { name: 'Cardiology' }))
    await user.type(screen.getByLabelText(/Registration number/), ' 10003456789 ')
    await user.click(screen.getByRole('button', { name: 'Add and send invitation' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/team/${DOCTOR_ID}`))
    expect(requests.find((request) => request.method === 'POST')?.body).toEqual({
      firstName: 'Daniel',
      lastName: 'Brown',
      email: 'daniel@demo.iatros.test',
      phone: null,
      role: 'DOCTOR',
      doctor: { primarySpecialty: 'CARDIOLOGY', additionalSpecialty: null, registrationNumber: '10003456789' },
    })
    expect(await screen.findByRole('heading', { name: 'Daniel Brown' })).toBeInTheDocument()
    expect(screen.getByText(/has not activated their account yet/)).toBeInTheDocument()
  })

  it('shows server validation next to the field and a conflict as a message', async () => {
    let attempt = 0
    fakeApi({
      ...owner,
      ...specialties,
      'POST /staff': () =>
        ++attempt === 1
          ? problem(400, 'VALIDATION_ERROR', { fieldErrors: [{ field: 'email', code: 'INVALID_EMAIL' }] })
          : problem(409, 'EMAIL_ALREADY_USED'),
    })
    renderRoute('/team/new')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('First name'), 'Sara')
    await user.type(screen.getByLabelText('Last name'), 'Adams')
    await user.type(screen.getByLabelText('Email'), 'sara@demo.iatros.test')
    await user.click(screen.getByRole('radio', { name: /Secretary/ }))
    await user.click(screen.getByRole('button', { name: 'Add and send invitation' }))

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Add and send invitation' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/already/i)
  })

  it('asks before leaving a form with unsaved changes', async () => {
    fakeApi({ ...owner, ...specialties, 'GET /staff': () => page([member()]) })
    const { router } = renderRoute('/team/new')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('First name'), 'Sara')
    await user.click(screen.getByRole('link', { name: 'Cancel' }))
    const dialog = await screen.findByRole('dialog', { name: 'Leave without saving?' })
    expect(router.state.location.pathname).toBe('/team/new')

    await user.click(within(dialog).getByRole('button', { name: 'Discard changes' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/team'))
  })

  it('goes back to the team and emails a password reset link', async () => {
    const { requests } = fakeApi({
      ...owner,
      ...specialties,
      [`GET /staff/${DOCTOR_ID}`]: () => json(member()),
      [`POST /staff/${DOCTOR_ID}/password-reset`]: () => json(null, 202),
    })
    renderRoute(`/team/${DOCTOR_ID}`)
    const user = userEvent.setup()

    expect(await screen.findByRole('link', { name: 'Back' })).toHaveAttribute('href', '/team')
    await user.click(screen.getByRole('button', { name: 'Reset password' }))
    const dialog = await screen.findByRole('dialog', { name: 'Reset password for Daniel Brown?' })
    await user.click(within(dialog).getByRole('button', { name: 'Reset password' }))

    expect(await screen.findByText('A password reset link has been sent to daniel@demo.iatros.test.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'POST' && request.path === `/staff/${DOCTOR_ID}/password-reset`)).toBe(true)
  })

  it('deactivates a member after confirmation', async () => {
    let current = member()
    const { requests } = fakeApi({
      ...owner,
      ...specialties,
      [`GET /staff/${DOCTOR_ID}`]: () => json(current),
      [`PATCH /staff/${DOCTOR_ID}/status`]: () => {
        current = member({ status: 'DEACTIVATED', version: 4 })
        return json(current)
      },
    })
    renderRoute(`/team/${DOCTOR_ID}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Deactivate' }))
    const dialog = await screen.findByRole('dialog', { name: 'Deactivate Daniel Brown?' })
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByText('Daniel Brown has been deactivated.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PATCH')?.body).toEqual({ status: 'DEACTIVATED' })
    expect(await screen.findByText(/This account is deactivated/)).toBeInTheDocument()
  })

  it('reports a blocked deactivation without changing the member', async () => {
    fakeApi({
      ...owner,
      ...specialties,
      [`GET /staff/${DOCTOR_ID}`]: () => json(member()),
      [`PATCH /staff/${DOCTOR_ID}/status`]: () => problem(422, 'STAFF_HAS_UPCOMING_APPOINTMENTS'),
    })
    renderRoute(`/team/${DOCTOR_ID}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Deactivate' }))
    const dialog = await screen.findByRole('dialog', { name: 'Deactivate Daniel Brown?' })
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByText(/upcoming appointments/i)).toBeInTheDocument()
    expect(screen.queryByText(/This account is deactivated/)).not.toBeInTheDocument()
  })

  it('edits contact details with the current version and locks the email once activated', async () => {
    const { requests } = fakeApi({
      ...owner,
      ...specialties,
      [`GET /staff/${DOCTOR_ID}`]: () => json(member()),
      [`PUT /staff/${DOCTOR_ID}`]: () => json(member({ phone: '+30 210 000 0000', version: 4 })),
    })
    renderRoute(`/team/${DOCTOR_ID}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit Daniel Brown' })
    expect(within(dialog).getByLabelText('Email')).toHaveAttribute('readonly')
    await user.type(within(dialog).getByLabelText(/Phone/), '+30 210 000 0000')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Changes saved.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PUT')?.body).toEqual({
      firstName: 'Daniel',
      lastName: 'Brown',
      email: 'daniel@demo.iatros.test',
      phone: '+30 210 000 0000',
      version: 3,
    })
  })

  it('shows a not-found state for a member outside the clinic', async () => {
    fakeApi({ ...owner, ...specialties, [`GET /staff/${DOCTOR_ID}`]: () => problem(404, 'STAFF_NOT_FOUND') })
    renderRoute(`/team/${DOCTOR_ID}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('This content could not be loaded')
  })

  it.each(['DOCTOR', 'SECRETARY'] as const)('is not available to the %s role', async (role) => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(testUser(role)) })
    renderRoute('/team/new')

    expect(await screen.findByRole('heading', { name: 'This page is not available' })).toBeInTheDocument()
    expect(requests.some((request) => request.path.startsWith('/staff'))).toBe(false)
  })
})
