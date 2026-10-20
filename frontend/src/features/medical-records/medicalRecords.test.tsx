import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { instantOf, localDateTimeOf } from '../../shared/lib/localDates'
import { fakeApi, json, noContent, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { Patient } from '../patients/api'
import type { Allergy, MedicalNote, MedicalRecord } from './api'
import type { Consultation } from './consultationsApi'
import type { MedicalDocument } from './documentsApi'

const PATIENT_ID = '0190a000-0000-7000-8000-0000000000p1'
const OWN_DOCTOR_ID = '0190a000-0000-7000-8000-000000000002'
const RECORD = `/patients/${PATIENT_ID}/medical-record`
const CONSULTATION_ID = '0190a000-0000-7000-8000-0000000000c1'

const patient = (overrides: Partial<Patient> = {}): Patient => ({
  id: PATIENT_ID,
  firstName: 'Alice',
  lastName: 'Martin',
  dateOfBirth: '1980-05-01',
  gender: 'FEMALE',
  phone: null,
  email: null,
  address: null,
  emergencyContactName: null,
  emergencyContactPhone: null,
  status: 'ACTIVE',
  archivedAt: null,
  createdAt: '2026-09-01T08:30:00Z',
  version: 0,
  primaryDoctor: { id: OWN_DOCTOR_ID, firstName: 'Daniel', lastName: 'Brown', status: 'ACTIVE', relationshipType: 'PRIMARY' },
  doctors: [],
  nextAppointmentAt: null,
  lastVisitAt: null,
  ...overrides,
})

const penicillin: Allergy = {
  id: 'a1',
  allergen: 'Penicillin',
  reaction: 'Hives',
  severity: 'SEVERE',
  notes: null,
  updatedAt: '2026-09-02T08:00:00Z',
  version: 3,
}

const record = (overrides: Partial<MedicalRecord> = {}): MedicalRecord => ({
  patientId: PATIENT_ID,
  allergies: [penicillin],
  conditions: [],
  medications: [],
  history: [],
  ...overrides,
})

const note = (id: string, content: string, editable: boolean): MedicalNote => ({
  id,
  content,
  author: { id: 'u1', firstName: editable ? 'Elena' : 'Chloe', lastName: editable ? 'Martin' : 'Carter' },
  createdAt: '2026-10-01T07:00:00Z',
  updatedAt: '2026-10-01T07:00:00Z',
  edited: false,
  editable,
  version: 0,
})

const consultation = (overrides: Partial<Consultation> = {}): Consultation => ({
  id: CONSULTATION_ID,
  patientId: PATIENT_ID,
  consultedAt: '2026-10-05T07:30:00Z',
  doctor: { id: OWN_DOCTOR_ID, firstName: 'Daniel', lastName: 'Brown' },
  reason: 'Chest pain',
  diagnosis: 'Angina',
  clinicalNotes: 'Examined.',
  treatmentNotes: null,
  followUpNotes: null,
  followUpOn: null,
  appointmentId: null,
  createdAt: '2026-10-05T08:00:00Z',
  updatedAt: '2026-10-05T08:00:00Z',
  editable: true,
  version: 0,
  ...overrides,
})

const page = <T,>(content: T[]) => json({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })

const scan = (overrides: Partial<MedicalDocument> = {}): MedicalDocument => ({
  id: 'doc1',
  patientId: PATIENT_ID,
  consultationId: null,
  fileName: 'blood-test.pdf',
  contentType: 'application/pdf',
  sizeBytes: 1_450_000,
  uploadedBy: { id: 'u1', firstName: 'Elena', lastName: 'Martin' },
  uploadedAt: '2026-10-02T07:00:00Z',
  archivable: true,
  ...overrides,
})

interface RecordApiOptions {
  patient?: Patient
  record?: MedicalRecord
  notes?: MedicalNote[]
  documents?: MedicalDocument[]
}

function recordApi(role: 'OWNER' | 'DOCTOR', options: RecordApiOptions = {}) {
  return {
    'GET /auth/me': () => json(testUser(role)),
    [`GET /patients/${PATIENT_ID}`]: () => json(options.patient ?? patient()),
    [`GET ${RECORD}`]: () => json(options.record ?? record()),
    [`GET ${RECORD}/notes`]: () => page(options.notes ?? []),
    [`GET /patients/${PATIENT_ID}/consultations`]: () => page([]),
    [`GET /patients/${PATIENT_ID}/documents`]: () => page(options.documents ?? []),
  }
}

describe('medical records', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('shows the structured sections, notes, and consultations', async () => {
    fakeApi(recordApi('DOCTOR', { notes: [note('n1', 'Sleeps better.', true)] }))
    renderRoute(RECORD)

    const allergies = await screen.findByRole('list', { name: 'Allergies' })
    expect(within(allergies).getByText('Penicillin')).toBeInTheDocument()
    expect(within(allergies).getByText('Severe')).toBeInTheDocument()
    expect(screen.getByText('No conditions recorded.')).toBeInTheDocument()
    expect(await screen.findByText('Sleeps better.')).toBeInTheDocument()
    expect(await screen.findByText('No consultations recorded yet.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'New consultation' })).toHaveAttribute(
      'href',
      `/patients/${PATIENT_ID}/consultations/new`,
    )
  })

  it('never offers the medical record to secretaries', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('SECRETARY')),
      [`GET /patients/${PATIENT_ID}`]: () => json(patient({ doctors: null })),
    })
    renderRoute(RECORD)

    expect(await screen.findByRole('heading', { name: 'This page is not available' })).toBeInTheDocument()
    expect(requests.some((request) => request.path.startsWith(RECORD))).toBe(false)

    renderRoute(`/patients/${PATIENT_ID}`)
    expect(await screen.findAllByText('Alice Martin')).not.toHaveLength(0)
    expect(screen.queryByRole('link', { name: 'Medical record' })).not.toBeInTheDocument()
  })

  it('links doctors from the patient profile to the record', async () => {
    fakeApi(recordApi('DOCTOR'))
    renderRoute(`/patients/${PATIENT_ID}`)

    expect(await screen.findByRole('link', { name: 'Medical record' })).toHaveAttribute('href', RECORD)
  })

  it('adds an allergy and validates it first', async () => {
    const { requests } = fakeApi({ ...recordApi('OWNER'), [`POST ${RECORD}/allergies`]: () => json(penicillin, 201) })
    renderRoute(RECORD)
    const user = userEvent.setup()

    const section = (await screen.findByRole('list', { name: 'Allergies' })).closest('section')!
    await user.click(within(section).getByRole('button', { name: 'Add' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add an allergy' })
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    expect(await within(dialog).findByText('This field is required.')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('Allergen'), ' Latex ')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Saved.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'POST')?.body).toEqual({
      allergen: 'Latex',
      reaction: null,
      severity: null,
      notes: null,
    })
  })

  it('edits an allergy with its current version and reports a conflict', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`PUT ${RECORD}/allergies/a1`]: () => problem(409, 'CONCURRENT_MODIFICATION'),
    })
    renderRoute(RECORD)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit Penicillin' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit allergy' })
    await user.clear(within(dialog).getByLabelText(/^Reaction/))
    await user.type(within(dialog).getByLabelText(/^Reaction/), 'Rash')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await within(dialog).findByText(/Someone else changed this item/)).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PUT')?.body).toEqual({
      allergen: 'Penicillin',
      reaction: 'Rash',
      severity: 'SEVERE',
      notes: null,
      version: 3,
    })
  })

  it('offers edit and remove only on the caller’s own notes', async () => {
    fakeApi(recordApi('DOCTOR', { notes: [note('n1', 'My note', true), note('n2', 'Colleague note', false)] }))
    renderRoute(RECORD)

    const notes = await screen.findByRole('list', { name: 'General notes' })
    const items = within(notes).getAllByRole('listitem')
    expect(within(items[0]!).getAllByRole('button')).toHaveLength(2)
    expect(within(items[1]!).queryAllByRole('button')).toHaveLength(0)
    expect(items[1]).toHaveTextContent('Chloe Carter')
  })

  it('adds a note', async () => {
    const { requests } = fakeApi({ ...recordApi('DOCTOR'), [`POST ${RECORD}/notes`]: () => json(note('n3', 'New', true), 201) })
    renderRoute(RECORD)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('New note'), 'Patient doing well.')
    await user.click(screen.getByRole('button', { name: 'Add note' }))

    expect(await screen.findByText('Note added.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'POST')?.body).toEqual({ content: 'Patient doing well.' })
  })

  it('makes an archived patient’s record read-only', async () => {
    fakeApi(recordApi('OWNER', { patient: patient({ status: 'ARCHIVED', archivedAt: '2026-10-01T08:00:00Z' }) }))
    renderRoute(RECORD)

    expect(await screen.findByText(/can be read but not changed/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Penicillin' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('New note')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'New consultation' })).not.toBeInTheDocument()
  })

  it('does not let an owner without a doctor profile start a consultation', async () => {
    fakeApi({ ...recordApi('OWNER'), 'GET /auth/me': () => json(testUser('OWNER', { doctorId: null })) })
    renderRoute(RECORD)

    expect(await screen.findByText('No consultations recorded yet.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'New consultation' })).not.toBeInTheDocument()
  })
})

describe('consultations', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('records a consultation in the clinic’s time zone and opens it', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`POST /patients/${PATIENT_ID}/consultations`]: () => json(consultation(), 201),
      [`GET /consultations/${CONSULTATION_ID}`]: () => json(consultation()),
    })
    const { router } = renderRoute(`/patients/${PATIENT_ID}/consultations/new`)
    const user = userEvent.setup()

    const when = await screen.findByLabelText('Date and time')
    await user.clear(when)
    await user.type(when, '2026-10-05T10:30')
    await user.type(screen.getByLabelText('Reason for visit'), 'Chest pain')
    await user.type(screen.getByLabelText(/^Follow-up date/), '2026-10-04')
    await user.click(screen.getByRole('button', { name: 'Save consultation' }))
    expect(await screen.findByText('The follow-up cannot be before the consultation.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText(/^Follow-up date/))
    await user.click(screen.getByRole('button', { name: 'Save consultation' }))

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/patients/${PATIENT_ID}/consultations/${CONSULTATION_ID}`),
    )
    expect(requests.find((request) => request.method === 'POST')?.body).toEqual({
      consultedAt: '2026-10-05T07:30:00.000Z',
      reason: 'Chest pain',
      diagnosis: null,
      clinicalNotes: null,
      treatmentNotes: null,
      followUpNotes: null,
      followUpOn: null,
      appointmentId: null,
    })
  })

  it('links a consultation started from an appointment and prefills its reason', async () => {
    const APPOINTMENT_ID = '0190a000-0000-7000-8000-0000000000a1'
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`GET /appointments/${APPOINTMENT_ID}`]: () => json({ id: APPOINTMENT_ID, reason: 'Knee pain' }),
      [`POST /patients/${PATIENT_ID}/consultations`]: () => json(consultation(), 201),
      [`GET /consultations/${CONSULTATION_ID}`]: () => json(consultation()),
    })
    renderRoute(`/patients/${PATIENT_ID}/consultations/new?appointment=${APPOINTMENT_ID}`)
    const user = userEvent.setup()

    expect(await screen.findByLabelText('Reason for visit')).toHaveValue('Knee pain')
    await user.click(screen.getByRole('button', { name: 'Save consultation' }))

    await waitFor(() => expect(requests.find((request) => request.method === 'POST')).toBeDefined())
    expect(requests.find((request) => request.method === 'POST')?.body).toMatchObject({
      reason: 'Knee pain',
      appointmentId: APPOINTMENT_ID,
    })
  })

  it('shows another doctor’s consultation as read-only', async () => {
    fakeApi({
      ...recordApi('DOCTOR'),
      [`GET /consultations/${CONSULTATION_ID}`]: () =>
        json(consultation({ editable: false, doctor: { id: 'd2', firstName: 'Chloe', lastName: 'Carter' } })),
    })
    renderRoute(`/patients/${PATIENT_ID}/consultations/${CONSULTATION_ID}`)

    expect(await screen.findByText('Only Chloe Carter can edit this consultation.')).toBeInTheDocument()
    expect(screen.getByText('Angina')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument()
  })

  it('edits the doctor’s own consultation with its version', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`GET /consultations/${CONSULTATION_ID}`]: () => json(consultation()),
      [`PUT /consultations/${CONSULTATION_ID}`]: () => json(consultation({ diagnosis: 'Stable angina', version: 1 })),
    })
    const { router } = renderRoute(`/patients/${PATIENT_ID}/consultations/${CONSULTATION_ID}/edit`)
    const user = userEvent.setup()

    const diagnosis = await screen.findByLabelText(/^Diagnosis/)
    await user.clear(diagnosis)
    await user.type(diagnosis, 'Stable angina')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/patients/${PATIENT_ID}/consultations/${CONSULTATION_ID}`),
    )
    expect(requests.find((request) => request.method === 'PUT')?.body).toMatchObject({
      consultedAt: '2026-10-05T07:30:00.000Z',
      diagnosis: 'Stable angina',
      version: 0,
    })
  })
})

describe('documents', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  const fileInput = () => document.querySelector<HTMLInputElement>('input[type="file"]')!
  const pdf = (name = 'referral.pdf', size = 4) => new File([new Uint8Array(size)], name, { type: 'application/pdf' })

  it('lists documents with view and download links, and archives only those the caller may archive', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR', { documents: [scan(), scan({ id: 'doc2', fileName: 'x-ray.png', sizeBytes: 820_000, archivable: false })] }),
      'DELETE /documents/doc1': noContent,
    })
    renderRoute(RECORD)
    const user = userEvent.setup()

    const table = await screen.findByRole('table', { name: 'Documents' })
    expect(within(table).getByRole('link', { name: 'blood-test.pdf' })).toHaveAttribute('href', '/api/v1/documents/doc1/content')
    expect(within(table).getByRole('link', { name: 'Download blood-test.pdf' })).toHaveAttribute(
      'href',
      '/api/v1/documents/doc1/content?download=true',
    )
    expect(within(table).getByText('1.5 MB')).toBeInTheDocument()
    expect(within(table).getByText('820 kB')).toBeInTheDocument()
    expect(within(table).queryByRole('button', { name: 'Archive x-ray.png' })).not.toBeInTheDocument()

    await user.click(within(table).getByRole('button', { name: 'Archive blood-test.pdf' }))
    const dialog = await screen.findByRole('dialog', { name: 'Archive blood-test.pdf?' })
    await user.click(within(dialog).getByRole('button', { name: 'Archive' }))

    expect(await screen.findByText('blood-test.pdf was archived.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'DELETE' && request.path === '/documents/doc1')).toBe(true)
  })

  it('uploads a file as multipart form data', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`POST /patients/${PATIENT_ID}/documents`]: () => json(scan({ fileName: 'referral.pdf' }), 201),
    })
    renderRoute(RECORD)
    const user = userEvent.setup()

    expect(await screen.findByText(/No documents yet/)).toBeInTheDocument()
    await user.upload(fileInput(), pdf())

    expect(await screen.findByText('referral.pdf was added.')).toBeInTheDocument()
    const body = requests.find((request) => request.method === 'POST')?.body as FormData
    expect((body.get('file') as File).name).toBe('referral.pdf')
    expect(body.has('consultationId')).toBe(false)
  })

  it('rejects unsupported and oversized files before uploading, and shows server validation errors', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`POST /patients/${PATIENT_ID}/documents`]: () =>
        problem(400, 'VALIDATION_ERROR', { fieldErrors: [{ field: 'file', code: 'FILE_TYPE_MISMATCH' }] }),
    })
    renderRoute(RECORD)
    const user = userEvent.setup({ applyAccept: false })
    await screen.findByText(/No documents yet/)

    await user.upload(fileInput(), new File(['MZ'], 'setup.exe'))
    expect(await screen.findByText(/This file type is not accepted/)).toBeInTheDocument()

    await user.upload(fileInput(), pdf('huge.pdf', 20 * 1024 * 1024 + 1))
    expect(await screen.findByText('This file is larger than 20 MB.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'POST')).toBe(false)

    await user.upload(fileInput(), pdf('renamed.pdf'))
    expect(await screen.findByText(/does not match its extension/)).toBeInTheDocument()
  })

  it('keeps an archived patient’s documents readable but unchangeable', async () => {
    fakeApi(
      recordApi('OWNER', {
        patient: patient({ status: 'ARCHIVED', archivedAt: '2026-10-01T08:00:00Z' }),
        documents: [scan()],
      }),
    )
    renderRoute(RECORD)

    expect(await screen.findByRole('link', { name: 'Download blood-test.pdf' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Upload document' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archive blood-test.pdf' })).not.toBeInTheDocument()
  })

  it('lists and attaches documents to one consultation', async () => {
    const { requests } = fakeApi({
      ...recordApi('DOCTOR'),
      [`GET /consultations/${CONSULTATION_ID}`]: () => json(consultation()),
      [`POST /patients/${PATIENT_ID}/documents`]: () => json(scan({ consultationId: CONSULTATION_ID }), 201),
    })
    renderRoute(`/patients/${PATIENT_ID}/consultations/${CONSULTATION_ID}`)
    const user = userEvent.setup()

    await screen.findByText(/Documents attached to this consultation/)
    expect(await screen.findByRole('button', { name: 'Upload document' })).toBeInTheDocument()
    await user.upload(fileInput(), pdf())

    await waitFor(() => expect(requests.find((request) => request.method === 'POST')).toBeDefined())
    const list = requests.find((request) => request.path === `/patients/${PATIENT_ID}/documents` && request.method === 'GET')
    expect(list?.query.get('consultationId')).toBe(CONSULTATION_ID)
    expect((requests.find((request) => request.method === 'POST')?.body as FormData).get('consultationId')).toBe(
      CONSULTATION_ID,
    )
  })
})

describe('clinic-local date-times', () => {
  it('round-trips wall-clock times, including across daylight-saving changes', () => {
    expect(instantOf('2026-10-05T10:30', 'Europe/Athens')).toBe('2026-10-05T07:30:00.000Z')
    expect(instantOf('2026-01-15T10:30', 'Europe/Athens')).toBe('2026-01-15T08:30:00.000Z')
    expect(localDateTimeOf('2026-10-25T00:30:00Z', 'Europe/Athens')).toBe('2026-10-25T03:30')
    expect(localDateTimeOf('2026-10-25T01:30:00Z', 'Europe/Athens')).toBe('2026-10-25T03:30')
    expect(instantOf('2026-03-29T12:00', 'America/New_York')).toBe('2026-03-29T16:00:00.000Z')
  })
})
