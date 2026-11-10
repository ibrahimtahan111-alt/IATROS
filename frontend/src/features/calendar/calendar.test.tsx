import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi, json, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { Role } from '../auth/api'
import type { Appointment, CalendarData, CalendarEvent } from './api'

const OWN_DOCTOR_ID = '0190a000-0000-7000-8000-000000000002'
const OTHER_DOCTOR_ID = '0190a000-0000-7000-8000-0000000000d2'
const PATIENT_ID = '0190a000-0000-7000-8000-0000000000p1'
const APPOINTMENT_ID = '0190a000-0000-7000-8000-0000000000a1'
const WEEK = '/calendar?date=2026-10-12'

const alice = { id: PATIENT_ID, firstName: 'Alice', lastName: 'Martin' }
const daniel = { id: OWN_DOCTOR_ID, firstName: 'Daniel', lastName: 'Brown' }
const chloe = { id: OTHER_DOCTOR_ID, firstName: 'Chloe', lastName: 'Carter' }

function event(overrides: Partial<CalendarEvent> = {}): CalendarEvent {
  return {
    id: APPOINTMENT_ID,
    // 10:00–10:30 in Europe/Athens (UTC+3 in October).
    startAt: '2026-10-12T07:00:00Z',
    endAt: '2026-10-12T07:30:00Z',
    doctorId: OWN_DOCTOR_ID,
    patient: alice,
    type: 'FOLLOW_UP',
    status: 'SCHEDULED',
    ...overrides,
  }
}

function calendar(events: CalendarEvent[], doctors = [daniel]): CalendarData {
  return {
    timeZone: 'Europe/Athens',
    from: '2026-10-12',
    to: '2026-10-18',
    events,
    doctors: doctors.map((doctor) => ({
      ...doctor,
      days: [{ date: '2026-10-12', ranges: [{ start: '09:00', end: '17:00' }] }],
    })),
  }
}

function appointment(overrides: Partial<Appointment> = {}): Appointment {
  return {
    id: APPOINTMENT_ID,
    patient: alice,
    doctor: daniel,
    startAt: '2026-10-12T07:00:00Z',
    endAt: '2026-10-12T07:30:00Z',
    type: 'FOLLOW_UP',
    status: 'SCHEDULED',
    reason: 'Knee pain',
    notes: null,
    cancellationReason: null,
    statusChangedAt: null,
    createdBy: { id: 'u1', firstName: 'Sara', lastName: 'Lopez' },
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-01T08:00:00Z',
    editable: true,
    cancellable: true,
    nextStatuses: ['CONFIRMED', 'WAITING', 'NO_SHOW'],
    consultationId: null,
    version: 3,
    ...overrides,
  }
}

const page = <T,>(content: T[]) => json({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

function calendarApi(role: Role, data: CalendarData = calendar([event()])) {
  return {
    'GET /auth/me': () => json(testUser(role)),
    'GET /clinic': () => json({ settings: { timeZone: 'Europe/Athens', defaultAppointmentMinutes: 30, weekStartDay: 1 } }),
    'GET /calendar/events': () => json(data),
    'GET /doctors': () =>
      page([daniel, chloe].map((doctor) => ({ ...doctor, status: 'ACTIVE', role: 'DOCTOR', primarySpecialty: 'GENERAL_PRACTICE' }))),
  }
}

describe('calendar', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('shows the week of the requested date with its appointments', async () => {
    const { requests } = fakeApi(calendarApi('OWNER'))
    renderRoute(WEEK)

    expect(await screen.findByRole('button', { name: /Alice Martin/ })).toBeInTheDocument()
    const query = requests.find((request) => request.path === '/calendar/events')!.query
    expect(query.get('from')).toBe('2026-10-12')
    expect(query.get('to')).toBe('2026-10-18')
    expect(screen.getByRole('button', { name: 'Week' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('combobox', { name: 'Doctor' })).toBeInTheDocument()
  })

  it('jumps to a picked date in day, week, and month views', async () => {
    fakeApi(calendarApi('OWNER'))
    const { router } = renderRoute('/calendar?view=day&date=2026-10-12')
    const user = userEvent.setup()

    const picker = await screen.findByLabelText('Go to date')
    expect(picker).toHaveValue('2026-10-12')
    fireEvent.change(picker, { target: { value: '2026-11-03' } })
    await waitFor(() => expect(router.state.location.search).toContain('date=2026-11-03'))

    await user.click(screen.getByRole('button', { name: 'Week' }))
    await waitFor(() => expect(router.state.location.search).not.toContain('view=day'))
    expect(screen.getByLabelText('Go to date')).toHaveValue('2026-11-03')

    await user.click(screen.getByRole('button', { name: 'Month' }))
    await waitFor(() => expect(router.state.location.search).toContain('view=month'))
    expect(screen.getByLabelText('Go to date')).toHaveValue('2026-11-03')
  })

  it('shows one column per doctor in the day view', async () => {
    fakeApi(calendarApi('OWNER', calendar([event()], [daniel, chloe])))
    renderRoute('/calendar?view=day&date=2026-10-12')

    const danielColumn = await screen.findByTestId(`column-${OWN_DOCTOR_ID}`)
    expect(within(danielColumn).getByRole('button', { name: /Alice Martin/ })).toBeInTheDocument()
    expect(within(screen.getByTestId(`column-${OTHER_DOCTOR_ID}`)).queryByRole('button')).not.toBeInTheDocument()
  })

  it('hides cancelled appointments unless asked to show them', async () => {
    fakeApi(
      calendarApi(
        'OWNER',
        calendar([
          event(),
          event({ id: 'cancelled', patient: { id: 'p2', firstName: 'Bob', lastName: 'Stone' }, status: 'CANCELLED' }),
        ]),
      ),
    )
    renderRoute(WEEK)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: /Alice Martin/ })
    expect(screen.queryByRole('button', { name: /Bob Stone/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: 'Show cancelled' }))
    expect(await screen.findByRole('button', { name: /Bob Stone/ })).toBeInTheDocument()
  })

  it('opens an appointment and changes its status with the version it read', async () => {
    const { requests } = fakeApi({
      ...calendarApi('OWNER'),
      [`GET /appointments/${APPOINTMENT_ID}`]: () => json(appointment()),
      [`PATCH /appointments/${APPOINTMENT_ID}/status`]: () => json(appointment({ status: 'CONFIRMED', version: 4 })),
    })
    const { router } = renderRoute(WEEK)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Alice Martin/ }))
    const panel = await screen.findByRole('dialog', { name: 'Alice Martin' })
    expect(await within(panel).findByText('Knee pain')).toBeInTheDocument()
    expect(router.state.location.search).toContain(`appointment=${APPOINTMENT_ID}`)
    expect(within(panel).getByRole('link', { name: 'Medical record' })).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: 'Confirm' }))
    await waitFor(() =>
      expect(requests.find((request) => request.method === 'PATCH')?.body).toEqual({ status: 'CONFIRMED', version: 3 }),
    )
  })

  it('offers the treating doctor to start the visit’s consultation', async () => {
    fakeApi({
      ...calendarApi('DOCTOR'),
      [`GET /appointments/${APPOINTMENT_ID}`]: () => json(appointment({ status: 'WAITING', nextStatuses: ['IN_CONSULTATION'] })),
    })
    renderRoute(`${WEEK}&appointment=${APPOINTMENT_ID}`)

    const panel = await screen.findByRole('dialog', { name: 'Alice Martin' })
    expect(await within(panel).findByRole('link', { name: 'Start consultation' })).toHaveAttribute(
      'href',
      `/patients/${PATIENT_ID}/consultations/new?appointment=${APPOINTMENT_ID}`,
    )
  })

  it('never offers clinical links to secretaries', async () => {
    fakeApi({
      ...calendarApi('SECRETARY'),
      [`GET /appointments/${APPOINTMENT_ID}`]: () => json(appointment({ status: 'WAITING', nextStatuses: [] })),
    })
    renderRoute(`${WEEK}&appointment=${APPOINTMENT_ID}`)

    const panel = await screen.findByRole('dialog', { name: 'Alice Martin' })
    expect(await within(panel).findByRole('link', { name: 'Open patient' })).toBeInTheDocument()
    expect(within(panel).queryByRole('link', { name: 'Medical record' })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('link', { name: 'Start consultation' })).not.toBeInTheDocument()
  })

  it('hides edit and cancel when the server says the appointment can no longer change', async () => {
    fakeApi({
      ...calendarApi('OWNER'),
      [`GET /appointments/${APPOINTMENT_ID}`]: () =>
        json(appointment({ status: 'COMPLETED', editable: false, cancellable: false, nextStatuses: [] })),
    })
    renderRoute(`${WEEK}&appointment=${APPOINTMENT_ID}`)

    const panel = await screen.findByRole('dialog', { name: 'Alice Martin' })
    await within(panel).findByText('Knee pain')
    expect(within(panel).queryByRole('button', { name: 'Reschedule' })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Cancel appointment' })).not.toBeInTheDocument()
  })

  it('books for the signed-in doctor and keeps the form when the time was taken', async () => {
    let attempts = 0
    const { requests } = fakeApi({
      ...calendarApi('DOCTOR'),
      'GET /patients': () =>
        page([{ ...alice, dateOfBirth: '1980-05-01', gender: null, phone: null, email: null, status: 'ACTIVE', primaryDoctor: null }]),
      'GET /appointments/availability': () =>
        json({
          timeZone: 'Europe/Athens',
          date: '2026-10-12',
          durationMinutes: 30,
          slots: [{ startAt: '2026-10-12T06:00:00Z', endAt: '2026-10-12T06:30:00Z' }],
        }),
      'POST /appointments': () => {
        attempts += 1
        return attempts === 1 ? problem(409, 'APPOINTMENT_CONFLICT') : json(appointment(), 201)
      },
      [`GET /appointments/${APPOINTMENT_ID}`]: () => json(appointment()),
    })
    const { router } = renderRoute(WEEK)
    const user = userEvent.setup()

    await screen.findByRole('button', { name: /Alice Martin/ })
    expect(screen.queryByRole('combobox', { name: 'Doctor' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Book appointment' }))
    const dialog = await screen.findByRole('dialog', { name: 'Book an appointment' })
    expect(within(dialog).queryByRole('combobox', { name: 'Doctor' })).not.toBeInTheDocument()

    await user.type(within(dialog).getByRole('searchbox'), 'Ali')
    await user.click(await within(dialog).findByRole('button', { name: /Alice Martin/ }))
    const slot = await within(dialog).findByRole('button', { name: /9:00/ })
    await user.click(slot)
    expect(slot).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(dialog).getByRole('button', { name: 'Book' }))
    expect(await within(dialog).findByText('This doctor already has an appointment at this time.')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Book' }))
    await waitFor(() => expect(router.state.location.search).toContain(`appointment=${APPOINTMENT_ID}`))
    const body = requests.filter((request) => request.method === 'POST').at(-1)!.body as Record<string, string>
    expect(body).toMatchObject({ patientId: PATIENT_ID, doctorId: OWN_DOCTOR_ID, type: 'FOLLOW_UP', reason: null })
    expect(Date.parse(body.endAt!) - Date.parse(body.startAt!)).toBe(30 * 60_000)
  })
})
