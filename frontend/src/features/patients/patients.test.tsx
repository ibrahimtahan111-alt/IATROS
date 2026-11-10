import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi, json, noContent, problem, testUser, type RecordedRequest } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { Doctor } from '../doctors/api'
import { ageOn, type Patient, type PatientDoctor, type PatientSummary } from './api'

const PATIENT_ID = '0190a000-0000-7000-8000-0000000000p1'
const OWN_DOCTOR_ID = '0190a000-0000-7000-8000-000000000002'
const OTHER_DOCTOR_ID = '0190a000-0000-7000-8000-0000000000d2'

function doctor(id: string, firstName: string, lastName: string, overrides: Partial<Doctor> = {}): Doctor {
  return {
    id,
    userId: `${id}-user`,
    firstName,
    lastName,
    email: `${firstName.toLowerCase()}@demo.iatros.test`,
    phone: null,
    role: 'DOCTOR',
    status: 'ACTIVE',
    primarySpecialty: 'GENERAL_PRACTICE',
    additionalSpecialty: null,
    registrationNumber: null,
    version: 0,
    ...overrides,
  }
}

const daniel = doctor(OWN_DOCTOR_ID, 'Daniel', 'Brown')
const chloe = doctor(OTHER_DOCTOR_ID, 'Chloe', 'Carter')
const danielRef: PatientDoctor = { id: OWN_DOCTOR_ID, firstName: 'Daniel', lastName: 'Brown', status: 'ACTIVE', relationshipType: 'PRIMARY' }
const chloeRef: PatientDoctor = { id: OTHER_DOCTOR_ID, firstName: 'Chloe', lastName: 'Carter', status: 'ACTIVE', relationshipType: 'AUTHORIZED' }

function patient(overrides: Partial<Patient> = {}): Patient {
  return {
    id: PATIENT_ID,
    firstName: 'Alice',
    lastName: 'Martin',
    dateOfBirth: '1980-05-01',
    gender: 'FEMALE',
    phone: '+30 210 000 0000',
    email: null,
    address: null,
    emergencyContactName: null,
    emergencyContactPhone: null,
    status: 'ACTIVE',
    archivedAt: null,
    createdAt: '2026-09-01T08:30:00Z',
    version: 2,
    primaryDoctor: danielRef,
    doctors: [danielRef, chloeRef],
    nextAppointmentAt: null,
    lastVisitAt: null,
    ...overrides,
  }
}

const page = <T,>(content: T[]) => json({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
const signedInAs = (role: Parameters<typeof testUser>[0]) => ({
  'GET /auth/me': () => json(testUser(role)),
  // The profile's appointments section.
  'GET /appointments': () => page([]),
  'GET /clinic': () => json({ settings: { timeZone: 'Europe/Athens', defaultAppointmentMinutes: 30, weekStartDay: 1 } }),
})
const doctors = (...list: Doctor[]) => ({ 'GET /doctors': () => page(list) })
const lastList = (requests: RecordedRequest[]) => requests.filter((request) => request.path === '/patients').at(-1)

/** The add-patient dialog once the doctors have loaded and the form is shown. */
async function addPatientForm() {
  const dialog = await screen.findByRole('dialog', { name: 'Add patient' })
  await within(dialog).findByLabelText('First name')
  return dialog
}

describe('patients', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('lists patients with their primary doctor', async () => {
    const summary: PatientSummary = patient()
    const { requests } = fakeApi({ ...signedInAs('OWNER'), ...doctors(daniel, chloe), 'GET /patients': () => page([summary]) })
    renderRoute('/patients')

    const table = await screen.findByRole('table', { name: 'Patients' })
    const row = within(table).getAllByRole('row')[1]!
    expect(within(row).getByRole('link', { name: 'Alice Martin' })).toHaveAttribute('href', `/patients/${PATIENT_ID}`)
    expect(within(row).getByText('Daniel Brown')).toBeInTheDocument()
    expect(within(row).getByText('+30 210 000 0000')).toBeInTheDocument()
    expect(screen.getByText('1 patient')).toBeInTheDocument()
    expect(lastList(requests)?.query.get('sort')).toBe('lastName,asc')
  })

  it('filters by primary doctor and archived status on the server', async () => {
    const { requests } = fakeApi({ ...signedInAs('OWNER'), ...doctors(daniel, chloe), 'GET /patients': () => page([patient()]) })
    const { router } = renderRoute('/patients')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('combobox', { name: 'Primary doctor' }))
    await user.click(await screen.findByRole('option', { name: 'Chloe Carter' }))
    await waitFor(() => expect(lastList(requests)?.query.get('primaryDoctorId')).toBe(OTHER_DOCTOR_ID))

    await user.click(screen.getByRole('combobox', { name: 'Status' }))
    await user.click(await screen.findByRole('option', { name: 'Archived' }))
    await waitFor(() => expect(lastList(requests)?.query.get('status')).toBe('ARCHIVED'))
    expect(router.state.location.search).toContain('status=ARCHIVED')
    expect(screen.getByRole('list', { name: 'Active filters' })).toHaveTextContent('Primary doctor: Chloe Carter')
  })

  it('does not offer the doctor filter to doctors, whose list is already their own', async () => {
    const { requests } = fakeApi({ ...signedInAs('DOCTOR'), 'GET /patients': () => page([]) })
    renderRoute('/patients')

    expect(await screen.findByRole('heading', { name: 'No patients yet' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Primary doctor' })).not.toBeInTheDocument()
    expect(requests.some((request) => request.path === '/doctors')).toBe(false)
  })

  it('adds a patient for the chosen doctor from a dialog over the list and opens the profile', async () => {
    const created = patient({ doctors: [danielRef] })
    const { requests } = fakeApi({
      ...signedInAs('SECRETARY'),
      ...doctors(daniel, chloe, doctor('d3', 'Gone', 'Doctor', { status: 'DEACTIVATED' })),
      'GET /patients': () => page([]),
      'POST /patients': () => json(created, 201),
      [`GET /patients/${PATIENT_ID}`]: () => json({ ...created, doctors: null }),
    })
    const { router } = renderRoute('/patients')
    const user = userEvent.setup()

    await user.click((await screen.findAllByRole('link', { name: 'Add patient' }))[0]!)
    const dialog = await addPatientForm()
    expect(router.state.location.pathname).toBe('/patients/new')
    expect(within(dialog).getByLabelText('First name')).toHaveFocus()
    expect(within(dialog).queryByLabelText(/^Email/)).not.toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Create patient' }))
    expect(await within(dialog).findAllByText('This field is required.')).toHaveLength(4)
    expect(within(dialog).getByLabelText('First name')).toHaveFocus()

    await user.type(within(dialog).getByLabelText('First name'), ' Alice ')
    await user.type(within(dialog).getByLabelText('Last name'), 'Martin')
    await user.type(within(dialog).getByLabelText('Date of birth'), '1980-05-01')
    await user.type(within(dialog).getByLabelText(/^Phone/), '+30 210 000 0000')
    expect(within(dialog).getByRole('combobox', { name: 'Country code' })).toHaveTextContent('GR +30')
    expect(within(dialog).getByLabelText(/^Phone/)).toHaveValue('210 000 0000')
    await user.click(within(dialog).getByRole('combobox', { name: 'Primary doctor' }))
    expect(screen.queryByRole('option', { name: 'Gone Doctor' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('option', { name: 'Daniel Brown' }))
    await user.click(within(dialog).getByRole('button', { name: 'Create patient' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/patients/${PATIENT_ID}`))
    expect(requests.find((request) => request.method === 'POST')?.body).toEqual({
      firstName: 'Alice',
      lastName: 'Martin',
      dateOfBirth: '1980-05-01',
      gender: null,
      phone: '+30 210 000 0000',
      email: null,
      address: null,
      emergencyContactName: null,
      emergencyContactPhone: null,
      primaryDoctorId: OWN_DOCTOR_ID,
    })
    expect(await screen.findByRole('heading', { name: 'Alice Martin' })).toBeInTheDocument()
  })

  it('expands to every field and reveals a server error on a field the compact dialog hides', async () => {
    const { requests } = fakeApi({
      ...signedInAs('OWNER'),
      ...doctors(daniel),
      'GET /patients': () => page([]),
      'POST /patients': () => problem(400, 'VALIDATION_ERROR', { fieldErrors: [{ field: 'address', code: 'INVALID_LENGTH' }] }),
    })
    renderRoute('/patients/new')
    const user = userEvent.setup()

    const dialog = await addPatientForm()
    await user.click(within(dialog).getByRole('button', { name: 'Show all fields' }))
    expect(within(dialog).getByLabelText(/^Email/)).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/^Emergency contact phone/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Show fewer fields' }))
    expect(within(dialog).queryByLabelText(/^Address/)).not.toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('First name'), 'Alice')
    await user.type(within(dialog).getByLabelText('Last name'), 'Martin')
    await user.type(within(dialog).getByLabelText('Date of birth'), '1980-05-01')
    await user.click(within(dialog).getByRole('button', { name: 'Create patient' }))

    await waitFor(() => expect(within(dialog).getByLabelText(/^Address/)).toHaveAttribute('aria-invalid', 'true'))
    expect(requests.find((request) => request.method === 'POST')?.body).toMatchObject({ phone: null, primaryDoctorId: OWN_DOCTOR_ID })
  })

  it('combines the chosen country code with the number, starting from the clinic’s country', async () => {
    const { requests } = fakeApi({
      ...signedInAs('OWNER'),
      'GET /clinic': () =>
        json({ phone: '+30 210 000 0000', settings: { timeZone: 'Europe/Athens', defaultAppointmentMinutes: 30, weekStartDay: 1 } }),
      ...doctors(daniel),
      'GET /patients': () => page([]),
      'POST /patients': () => json(patient(), 201),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient()),
    })
    renderRoute('/patients/new')
    const user = userEvent.setup()

    const dialog = await addPatientForm()
    const country = within(dialog).getByRole('combobox', { name: 'Country code' })
    await waitFor(() => expect(country).toHaveTextContent('GR +30'))
    await user.type(within(dialog).getByLabelText(/^Phone/), '690 000 0001')
    await user.click(country)
    await user.click(await screen.findByRole('option', { name: 'France (+33)' }))
    expect(country).toHaveTextContent('FR +33')

    await user.type(within(dialog).getByLabelText('First name'), 'Alice')
    await user.type(within(dialog).getByLabelText('Last name'), 'Martin')
    await user.type(within(dialog).getByLabelText('Date of birth'), '1980-05-01')
    await user.click(within(dialog).getByRole('button', { name: 'Create patient' }))

    await waitFor(() =>
      expect(requests.find((request) => request.method === 'POST')?.body).toMatchObject({ phone: '+33 690 000 0001' }),
    )
  })

  it('asks before discarding entered details and returns to the filtered list', async () => {
    const { requests } = fakeApi({
      ...signedInAs('OWNER'),
      ...doctors(daniel),
      'GET /patients': () => page([]),
    })
    const { router } = renderRoute('/patients/new?status=ARCHIVED')
    const user = userEvent.setup()

    const dialog = await addPatientForm()
    await user.type(within(dialog).getByLabelText('First name'), 'Alice')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    const confirm = await screen.findByRole('dialog', { name: 'Leave without saving?' })
    await user.click(within(confirm).getByRole('button', { name: 'Discard changes' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/patients'))
    expect(router.state.location.search).toBe('?status=ARCHIVED')
    expect(screen.queryByRole('dialog', { name: 'Add patient' })).not.toBeInTheDocument()
    expect(requests.some((request) => request.method === 'POST')).toBe(false)
  })

  it('makes a doctor the primary doctor of the patients they add and rejects future birth dates', async () => {
    const { requests } = fakeApi({
      ...signedInAs('DOCTOR'),
      ...doctors(daniel),
      'GET /patients': () => page([]),
      'POST /patients': () => json(patient(), 201),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient()),
    })
    const { router } = renderRoute('/patients/new')
    const user = userEvent.setup()

    expect(await screen.findByText('You will be the primary doctor: Daniel Brown.')).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Primary doctor' })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('First name'), 'Alice')
    await user.type(screen.getByLabelText('Last name'), 'Martin')
    await user.type(screen.getByLabelText('Date of birth'), '2999-01-01')
    await user.click(screen.getByRole('button', { name: 'Create patient' }))
    expect(await screen.findByText('This date cannot be in the future.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'POST')).toBe(false)

    await user.clear(screen.getByLabelText('Date of birth'))
    await user.type(screen.getByLabelText('Date of birth'), '1980-05-01')
    await user.click(screen.getByRole('button', { name: 'Create patient' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/patients/${PATIENT_ID}`))
    expect(requests.find((request) => request.method === 'POST')?.body).toMatchObject({ primaryDoctorId: OWN_DOCTOR_ID })
  })

  it('explains that a doctor must exist before adding patients', async () => {
    fakeApi({ ...signedInAs('OWNER'), ...doctors(), 'GET /patients': () => page([]) })
    renderRoute('/patients/new')

    const dialog = await screen.findByRole('dialog', { name: 'Add patient' })
    expect(await within(dialog).findByRole('heading', { name: 'No doctor available' })).toBeInTheDocument()
    expect(within(dialog).getByRole('link', { name: 'Add a doctor' })).toHaveAttribute('href', '/team/new')
    expect(within(dialog).queryByRole('button', { name: 'Create patient' })).not.toBeInTheDocument()
  })

  it('shows a secretary the primary doctor only, without management actions', async () => {
    fakeApi({ ...signedInAs('SECRETARY'), [`GET /patients/${PATIENT_ID}`]: () => json(patient({ doctors: null })) })
    renderRoute(`/patients/${PATIENT_ID}`)

    expect(await screen.findByRole('heading', { name: 'Alice Martin' })).toBeInTheDocument()
    expect(screen.getByText('Daniel Brown')).toBeInTheDocument()
    expect(screen.queryByText('Chloe Carter')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Change primary doctor' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archive patient' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit' })).toHaveAttribute('href', `/patients/${PATIENT_ID}/edit`)
  })

  it('lets an owner change the primary doctor', async () => {
    const { requests } = fakeApi({
      ...signedInAs('OWNER'),
      ...doctors(daniel, chloe),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient({ doctors: [danielRef] })),
      [`PATCH /patients/${PATIENT_ID}/primary-doctor`]: () =>
        json(
          patient({
            primaryDoctor: { ...chloeRef, relationshipType: 'PRIMARY' },
            doctors: [{ ...chloeRef, relationshipType: 'PRIMARY' }, { ...danielRef, relationshipType: 'AUTHORIZED' }],
          }),
        ),
    })
    renderRoute(`/patients/${PATIENT_ID}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Change primary doctor' }))
    const dialog = await screen.findByRole('dialog', { name: 'Change the primary doctor of Alice Martin' })
    await user.click(within(dialog).getByRole('button', { name: 'Change primary doctor' }))
    expect(await within(dialog).findByText('This field is required.')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('combobox', { name: 'Doctor' }))
    expect(screen.queryByRole('option', { name: 'Daniel Brown' })).not.toBeInTheDocument()
    await user.click(await screen.findByRole('option', { name: 'Chloe Carter' }))
    await user.click(within(dialog).getByRole('button', { name: 'Change primary doctor' }))

    expect(await screen.findByText('The primary doctor has been changed.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PATCH')?.body).toEqual({ doctorId: OTHER_DOCTOR_ID })
    const table = screen.getByRole('table', { name: 'Doctors with access to Alice Martin' })
    expect(within(table).getAllByRole('row')[2]).toHaveTextContent('Daniel BrownAuthorized')
  })

  it('lets an owner remove an authorized doctor after confirmation', async () => {
    let current = patient()
    const { requests } = fakeApi({
      ...signedInAs('OWNER'),
      ...doctors(daniel, chloe),
      [`GET /patients/${PATIENT_ID}`]: () => json(current),
      [`DELETE /patients/${PATIENT_ID}/doctors/${OTHER_DOCTOR_ID}`]: () => {
        current = patient({ doctors: [danielRef] })
        return noContent()
      },
    })
    renderRoute(`/patients/${PATIENT_ID}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Remove access for Chloe Carter' }))
    const dialog = await screen.findByRole('dialog', { name: 'Remove access for Chloe Carter?' })
    await user.click(within(dialog).getByRole('button', { name: 'Remove access' }))

    expect(await screen.findByText('Chloe Carter no longer has access.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'DELETE')).toBe(true)
    await waitFor(() => expect(screen.queryByText('Chloe Carter')).not.toBeInTheDocument())
  })

  it('reports a blocked archive without changing the patient', async () => {
    fakeApi({
      ...signedInAs('OWNER'),
      ...doctors(daniel, chloe),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient()),
      [`PATCH /patients/${PATIENT_ID}/status`]: () => problem(422, 'PATIENT_HAS_UPCOMING_APPOINTMENTS'),
    })
    renderRoute(`/patients/${PATIENT_ID}`)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Archive patient' }))
    const dialog = await screen.findByRole('dialog', { name: 'Archive Alice Martin?' })
    await user.click(within(dialog).getByRole('button', { name: 'Archive patient' }))

    expect(await screen.findByText(/still has upcoming appointments/)).toBeInTheDocument()
    expect(screen.queryByText(/This patient was archived/)).not.toBeInTheDocument()
  })

  it('shows an archived patient as read-only with a restore action for the owner', async () => {
    fakeApi({
      ...signedInAs('OWNER'),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient({ status: 'ARCHIVED', archivedAt: '2026-10-01T08:00:00Z' })),
    })
    renderRoute(`/patients/${PATIENT_ID}`)

    expect(await screen.findByText(/This patient was archived on Oct 1, 2026/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restore' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Give access' })).not.toBeInTheDocument()
  })

  it('edits details with the loaded version and returns to the profile', async () => {
    const { requests } = fakeApi({
      ...signedInAs('DOCTOR'),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient()),
      [`PUT /patients/${PATIENT_ID}`]: () => json(patient({ email: 'alice@example.test', version: 3 })),
    })
    const { router } = renderRoute(`/patients/${PATIENT_ID}/edit`)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/^Email/), 'alice@example.test')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(router.state.location.pathname).toBe(`/patients/${PATIENT_ID}`))
    expect(requests.find((request) => request.method === 'PUT')?.body).toEqual({
      firstName: 'Alice',
      lastName: 'Martin',
      dateOfBirth: '1980-05-01',
      gender: 'FEMALE',
      phone: '+30 210 000 0000',
      email: 'alice@example.test',
      address: null,
      emergencyContactName: null,
      emergencyContactPhone: null,
      version: 2,
    })
  })

  it('shows a not-found state for a patient outside the caller’s access', async () => {
    fakeApi({ ...signedInAs('DOCTOR'), [`GET /patients/${PATIENT_ID}`]: () => problem(404, 'PATIENT_NOT_FOUND') })
    renderRoute(`/patients/${PATIENT_ID}`)

    expect(await screen.findByRole('alert')).toHaveTextContent('This content could not be loaded')
  })

  it('computes ages from calendar dates', () => {
    expect(ageOn('1980-05-01', '2026-04-30')).toBe(45)
    expect(ageOn('1980-05-01', '2026-05-01')).toBe(46)
    expect(ageOn('2000-02-29', '2026-02-28')).toBe(25)
  })
})
