import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi, json, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { StaffMember } from '../team/api'
import type { Doctor } from './api'

const MEMBER_ID = '0190a000-0000-7000-8000-0000000000u1'
const DOCTOR_ID = '0190a000-0000-7000-8000-0000000000d1'

const member: StaffMember = {
  id: MEMBER_ID,
  firstName: 'Daniel',
  lastName: 'Brown',
  email: 'daniel@demo.iatros.test',
  phone: null,
  role: 'DOCTOR',
  status: 'ACTIVE',
  lastLoginAt: null,
  createdAt: '2026-09-01T08:30:00Z',
  version: 1,
  doctor: { id: DOCTOR_ID, primarySpecialty: 'CARDIOLOGY', additionalSpecialty: null, registrationNumber: null },
}

function doctor(overrides: Partial<Doctor> = {}): Doctor {
  return {
    id: DOCTOR_ID,
    userId: MEMBER_ID,
    firstName: 'Daniel',
    lastName: 'Brown',
    email: 'daniel@demo.iatros.test',
    phone: null,
    role: 'DOCTOR',
    status: 'ACTIVE',
    primarySpecialty: 'CARDIOLOGY',
    additionalSpecialty: null,
    registrationNumber: null,
    version: 4,
    ...overrides,
  }
}

const specialties = {
  'GET /specialties': () =>
    json([
      { id: 's1', code: 'CARDIOLOGY' },
      { id: 's2', code: 'PEDIATRICS' },
    ]),
}

describe('doctor profiles', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('lets an owner edit a doctor’s professional information from the team page', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('OWNER')),
      ...specialties,
      [`GET /staff/${MEMBER_ID}`]: () => json(member),
      [`GET /doctors/${DOCTOR_ID}`]: () => json(doctor()),
      [`PUT /doctors/${DOCTOR_ID}`]: () => json(doctor({ primarySpecialty: 'PEDIATRICS', version: 5 })),
    })
    renderRoute(`/team/${MEMBER_ID}`)
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Professional information' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Working hours' })).toHaveAttribute(
      'href',
      `/schedules?doctor=${DOCTOR_ID}`,
    )
    const editButtons = await screen.findAllByRole('button', { name: 'Edit' })
    await user.click(editButtons.at(-1)!)

    const dialog = await screen.findByRole('dialog', { name: 'Edit professional information for Daniel Brown' })
    await user.click(within(dialog).getByRole('combobox', { name: 'Primary specialty' }))
    await user.click(await screen.findByRole('option', { name: 'Pediatrics' }))
    await user.type(within(dialog).getByLabelText(/Registration number/), ' RPPS-42 ')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Professional information saved.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PUT')?.body).toEqual({
      primarySpecialty: 'PEDIATRICS',
      additionalSpecialty: null,
      registrationNumber: 'RPPS-42',
      version: 4,
    })
  })

  it('reports a concurrent edit inside the dialog', async () => {
    fakeApi({
      'GET /auth/me': () => json(testUser('DOCTOR', { doctorId: DOCTOR_ID })),
      ...specialties,
      [`GET /doctors/${DOCTOR_ID}`]: () => json(doctor()),
      [`PUT /doctors/${DOCTOR_ID}`]: () => problem(409, 'CONCURRENT_MODIFICATION'),
    })
    renderRoute('/account')
    const user = userEvent.setup()

    expect(await screen.findByText('Cardiology')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/Someone else changed this item/)
  })

  it('shows no professional section to a secretary', async () => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(testUser('SECRETARY')) })
    renderRoute('/account')

    expect(await screen.findByRole('heading', { name: 'Preferences' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Professional information' })).not.toBeInTheDocument()
    expect(requests.some((request) => request.path.startsWith('/doctors'))).toBe(false)
  })
})
