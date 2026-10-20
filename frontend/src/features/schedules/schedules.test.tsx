import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi, json, noContent, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { Doctor } from '../doctors/api'
import type { ScheduleException, WeeklySchedule } from './api'

const DANIEL = '0190a000-0000-7000-8000-0000000000d1'
const CHLOE = '0190a000-0000-7000-8000-0000000000d2'
const OWN_DOCTOR = testUser('DOCTOR').doctorId!

function doctor(id: string, firstName: string, lastName: string, overrides: Partial<Doctor> = {}): Doctor {
  return {
    id,
    userId: `user-${id}`,
    firstName,
    lastName,
    email: `${firstName.toLowerCase()}@demo.iatros.test`,
    phone: null,
    role: 'DOCTOR',
    status: 'ACTIVE',
    primarySpecialty: 'CARDIOLOGY',
    additionalSpecialty: null,
    registrationNumber: null,
    version: 0,
    ...overrides,
  }
}

function week(doctorId: string, overrides: Partial<WeeklySchedule> = {}): WeeklySchedule {
  return {
    doctorId,
    timeZone: 'Europe/Athens',
    version: 2,
    periods: [
      { dayOfWeek: 1, startTime: '09:00', endTime: '13:00' },
      { dayOfWeek: 1, startTime: '14:00', endTime: '18:00' },
    ],
    ...overrides,
  }
}

const dayOff: ScheduleException = {
  id: 'ex-1',
  date: '2030-03-04',
  type: 'UNAVAILABLE',
  startTime: null,
  endTime: null,
  reason: 'Conference',
  createdAt: '2030-01-01T10:00:00Z',
}

function scheduleHandlers(doctorId: string, exceptions: ScheduleException[] = []) {
  return {
    [`GET /doctors/${doctorId}/schedule`]: () => json(week(doctorId)),
    [`GET /doctors/${doctorId}/schedule/exceptions`]: () => json(exceptions),
    [`GET /doctors/${doctorId}/working-hours`]: () =>
      json({
        timeZone: 'Europe/Athens',
        days: [
          { date: '2030-03-04', ranges: [{ start: '09:00', end: '13:00' }] },
          { date: '2030-03-05', ranges: [] },
        ],
      }),
  }
}

const owner = {
  'GET /auth/me': () => json(testUser('OWNER', { doctorId: null })),
  'GET /doctors': () =>
    json({
      content: [doctor(DANIEL, 'Daniel', 'Brown'), doctor(CHLOE, 'Chloe', 'Carter', { status: 'DEACTIVATED' })],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    }),
}

const setTime = (input: HTMLElement, value: string) => fireEvent.change(input, { target: { value } })

describe('schedules', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
  })

  it('shows the first doctor’s week, exceptions, and upcoming hours to an owner', async () => {
    fakeApi({ ...owner, ...scheduleHandlers(DANIEL, [dayOff]) })
    renderRoute('/schedules')

    const monday = await screen.findByRole('group', { name: 'Monday' })
    expect(within(monday).getByLabelText('Monday, period 1, start')).toHaveValue('09:00')
    expect(within(monday).getByLabelText('Monday, period 2, end')).toHaveValue('18:00')
    expect(within(screen.getByRole('group', { name: 'Tuesday' })).getByText('Not working')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Doctor' })).toHaveTextContent('Daniel Brown')

    const exceptions = await screen.findByRole('table', { name: 'Upcoming exceptions' })
    expect(within(exceptions).getByText('All day')).toBeInTheDocument()
    expect(within(exceptions).getByText('Conference')).toBeInTheDocument()
    expect(await screen.findByRole('list', { name: 'Next two weeks' })).toHaveTextContent('Not working')
  })

  it('selects the doctor from the URL and flags a deactivated doctor', async () => {
    fakeApi({ ...owner, ...scheduleHandlers(CHLOE) })
    renderRoute(`/schedules?doctor=${CHLOE}`)

    expect(await screen.findByText(/Chloe Carter is deactivated/)).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Doctor' })).toHaveTextContent('Chloe Carter (deactivated)')
  })

  it('saves the whole week with the version it was loaded from', async () => {
    const { requests } = fakeApi({
      ...owner,
      ...scheduleHandlers(DANIEL),
      [`PUT /doctors/${DANIEL}/schedule`]: (request) =>
        json(week(DANIEL, { version: 3, periods: (request.body as WeeklySchedule).periods })),
    })
    renderRoute('/schedules')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Add period to Tuesday' }))
    const tuesday = screen.getByRole('group', { name: 'Tuesday' })
    expect(within(tuesday).getByLabelText('Tuesday, period 1, start')).toHaveValue('09:00')
    setTime(within(tuesday).getByLabelText('Tuesday, period 1, end'), '12:30')
    await user.click(screen.getByRole('button', { name: 'Save weekly hours' }))

    expect(await screen.findByText(/Weekly hours saved/)).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'PUT')?.body).toEqual({
      version: 2,
      periods: [
        { dayOfWeek: 1, startTime: '09:00', endTime: '13:00' },
        { dayOfWeek: 1, startTime: '14:00', endTime: '18:00' },
        { dayOfWeek: 2, startTime: '09:00', endTime: '12:30' },
      ],
    })
    expect(screen.getByRole('button', { name: 'Save weekly hours' })).toBeDisabled()
  })

  it('checks inverted and overlapping periods before saving', async () => {
    const { requests } = fakeApi({ ...owner, ...scheduleHandlers(DANIEL) })
    renderRoute('/schedules')
    const user = userEvent.setup()

    const monday = await screen.findByRole('group', { name: 'Monday' })
    setTime(within(monday).getByLabelText('Monday, period 2, start'), '12:00')
    await user.click(screen.getByRole('button', { name: 'Save weekly hours' }))
    expect(await within(monday).findByText('This period overlaps another one on the same day.')).toBeInTheDocument()

    setTime(within(monday).getByLabelText('Monday, period 2, start'), '19:00')
    await user.click(screen.getByRole('button', { name: 'Save weekly hours' }))
    expect(await within(monday).findByText('The end must be after the start.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'PUT')).toBe(false)
  })

  it('reports a concurrent change and reloads the latest week on request', async () => {
    fakeApi({
      ...owner,
      ...scheduleHandlers(DANIEL),
      [`PUT /doctors/${DANIEL}/schedule`]: () => problem(409, 'CONCURRENT_MODIFICATION'),
    })
    renderRoute('/schedules')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Remove Monday, period 2' }))
    await user.click(screen.getByRole('button', { name: 'Save weekly hours' }))
    expect(await screen.findByText(/Someone else changed this item/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Load latest hours' }))
    expect(screen.getByLabelText('Monday, period 2, end')).toHaveValue('18:00')
    expect(screen.queryByText(/Someone else changed this item/)).not.toBeInTheDocument()
  })

  it('asks before switching doctors with unsaved changes', async () => {
    fakeApi({ ...owner, ...scheduleHandlers(DANIEL), ...scheduleHandlers(CHLOE) })
    const { router } = renderRoute('/schedules')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Add period to Friday' }))
    await user.click(screen.getByRole('combobox', { name: 'Doctor' }))
    await user.click(await screen.findByRole('option', { name: 'Chloe Carter (deactivated)' }))
    const dialog = await screen.findByRole('dialog', { name: 'Leave without saving?' })
    expect(router.state.location.search).toBe('')

    await user.click(within(dialog).getByRole('button', { name: 'Discard changes' }))
    await waitFor(() => expect(router.state.location.search).toBe(`?doctor=${CHLOE}`))
  })

  it('adds an all-day absence and requires times for extra hours', async () => {
    const { requests } = fakeApi({
      ...owner,
      ...scheduleHandlers(DANIEL),
      [`POST /doctors/${DANIEL}/schedule/exceptions`]: () => json(dayOff, 201),
    })
    renderRoute('/schedules')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Add exception' }))
    let dialog = await screen.findByRole('dialog', { name: 'Add an exception' })
    await user.click(within(dialog).getByRole('combobox', { name: 'Type' }))
    await user.click(await screen.findByRole('option', { name: 'Extra hours' }))
    await user.click(within(dialog).getByRole('button', { name: 'Add exception' }))
    expect(await within(dialog).findAllByText('This field is required.')).toHaveLength(2)

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await user.click(screen.getByRole('button', { name: 'Add exception' }))
    dialog = await screen.findByRole('dialog', { name: 'Add an exception' })
    fireEvent.change(within(dialog).getByLabelText('Date'), { target: { value: '2030-03-04' } })
    await user.type(within(dialog).getByLabelText(/Reason/), ' Conference ')
    await user.click(within(dialog).getByRole('button', { name: 'Add exception' }))

    expect(await screen.findByText('Exception added.')).toBeInTheDocument()
    expect(requests.find((request) => request.method === 'POST')?.body).toEqual({
      date: '2030-03-04',
      type: 'UNAVAILABLE',
      startTime: null,
      endTime: null,
      reason: 'Conference',
    })
  })

  it('shows a past date rejected by the server next to the date', async () => {
    fakeApi({
      ...owner,
      ...scheduleHandlers(DANIEL),
      [`POST /doctors/${DANIEL}/schedule/exceptions`]: () =>
        problem(400, 'VALIDATION_ERROR', { fieldErrors: [{ field: 'date', code: 'DATE_IN_PAST' }] }),
    })
    renderRoute('/schedules')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Add exception' }))
    const dialog = await screen.findByRole('dialog', { name: 'Add an exception' })
    await user.click(within(dialog).getByRole('button', { name: 'Add exception' }))

    expect(await within(dialog).findByText('Choose today or a later date.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Date')).toHaveFocus()
  })

  it('removes an exception after confirmation', async () => {
    const { requests } = fakeApi({
      ...owner,
      ...scheduleHandlers(DANIEL, [dayOff]),
      [`DELETE /doctors/${DANIEL}/schedule/exceptions/${dayOff.id}`]: () => noContent(),
    })
    renderRoute('/schedules')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /Remove exception on/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Remove this exception?' })
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }))

    expect(await screen.findByText('Exception removed.')).toBeInTheDocument()
    expect(requests.some((request) => request.method === 'DELETE')).toBe(true)
  })

  it('shows a doctor their own schedule without a doctor picker', async () => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(testUser('DOCTOR')), ...scheduleHandlers(OWN_DOCTOR) })
    renderRoute('/schedules')

    expect(await screen.findByRole('heading', { name: 'My schedule' })).toBeInTheDocument()
    expect(await screen.findByRole('group', { name: 'Monday' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Doctor' })).not.toBeInTheDocument()
    expect(requests.some((request) => request.path === '/doctors')).toBe(false)
  })

  it('invites the owner to add a doctor when there is none', async () => {
    fakeApi({
      'GET /auth/me': () => json(testUser('OWNER', { doctorId: null })),
      'GET /doctors': () => json({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
    })
    renderRoute('/schedules')

    expect(await screen.findByRole('heading', { name: 'No doctors yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add a doctor' })).toHaveAttribute('href', '/team/new')
  })

  it('is not available to secretaries', async () => {
    const { requests } = fakeApi({ 'GET /auth/me': () => json(testUser('SECRETARY')) })
    renderRoute('/schedules')

    expect(await screen.findByRole('heading', { name: 'This page is not available' })).toBeInTheDocument()
    expect(requests.some((request) => request.path.startsWith('/doctors'))).toBe(false)
  })
})
