import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../../i18n'
import { fakeApi, json, problem, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import { activityItem, alice, chloe, daniel, kpis } from '../activity/fixtures'
import type { DoctorDashboard, OwnerDashboard, ScheduleItem } from './api'

const summary = {
  appointments: 5,
  patients: 4,
  remaining: 2,
  waiting: 1,
  inConsultation: 1,
  completed: 1,
  noShows: 0,
  cancelled: 1,
}

function scheduleItem(overrides: Partial<ScheduleItem> = {}): ScheduleItem {
  return {
    id: '0190a000-0000-7000-8000-0000000000a1',
    // 10:00 in Europe/Athens.
    startAt: '2026-10-10T07:00:00Z',
    endAt: '2026-10-10T07:30:00Z',
    patient: alice,
    doctor: daniel,
    type: 'FOLLOW_UP',
    status: 'WAITING',
    alerts: null,
    ...overrides,
  }
}

const ownerDashboard: OwnerDashboard = {
  timeZone: 'Europe/Athens',
  today: '2026-10-10',
  summary,
  activePatients: 214,
  activeDoctors: 2,
  doctors: [
    {
      doctor: daniel,
      appointments: 3,
      completed: 1,
      inProgress: 1,
      remaining: 1,
      noShows: 0,
    },
    {
      doctor: chloe,
      appointments: 2,
      completed: 0,
      inProgress: 1,
      remaining: 1,
      noShows: 0,
    },
  ],
  schedule: [scheduleItem()],
  scheduleTruncated: false,
  recentActivity: [activityItem()],
}

const doctorDashboard: DoctorDashboard = {
  timeZone: 'Europe/Athens',
  today: '2026-10-10',
  doctor: daniel,
  summary: { ...summary, appointments: 3, patients: 3 },
  upcomingAppointments: 9,
  patients: 42,
  primaryPatients: 30,
  schedule: [
    scheduleItem({
      alerts: {
        severeAllergies: ['Penicillin'],
        activeConditions: ['Hypertension'],
      },
    }),
  ],
  recentPatients: [{ patient: alice, lastVisitAt: '2026-10-08T07:00:00Z' }],
  recentActivity: [],
}

describe('dashboards', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en')
  })

  it('gives owners the clinic day, doctors, performance, and recent activity', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('OWNER')),
      'GET /dashboard/owner': () => json(ownerDashboard),
      'GET /kpis/clinic': () => json(kpis()),
    })
    renderRoute('/dashboard')

    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    expect(await screen.findByText(/Good (morning|afternoon|evening), Elena/)).toBeInTheDocument()
    const today = screen.getByRole('group', { name: 'Today' })
    expect(within(today).getByRole('link', { name: /In consultation\s*1/ })).toHaveAttribute(
      'href',
      '/calendar?view=day&date=2026-10-10',
    )
    expect(within(today).getByRole('link', { name: /Still to come\s*2/ })).toBeInTheDocument()
    expect(within(today).queryByRole('link', { name: /No-show/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Total patients/ })).not.toBeInTheDocument()

    const waitingRoom = screen.getByRole('region', { name: 'Waiting room' })
    expect(within(waitingRoom).getByRole('link', { name: 'Alice Martin' })).toHaveAttribute(
      'href',
      '/calendar?view=day&date=2026-10-10&appointment=0190a000-0000-7000-8000-0000000000a1',
    )
    expect(within(waitingRoom).getByText('Daniel Brown · Follow-up')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'In consultation' })).toHaveTextContent('No consultation in progress.')
    expect(screen.getByRole('link', { name: 'Daniel Brown' })).toHaveAttribute(
      'href',
      '/calendar?view=day&date=2026-10-10&doctor=0190a000-0000-7000-8000-000000000002',
    )
    expect(screen.getByText('3 appointments')).toBeInTheDocument()
    const switcher = screen.getByRole('group', { name: 'Overview or activity' })
    expect(within(switcher).getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
    expect(within(switcher).getByRole('link', { name: 'Activity' })).toHaveAttribute('href', '/activity')
    expect(requests.some((request) => request.path === '/kpis/clinic')).toBe(false)
  })

  it('gives doctors only their own day, with clinical alerts and their own KPIs', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('DOCTOR')),
      'GET /dashboard/doctor': () => json(doctorDashboard),
      [`GET /kpis/doctors/${daniel.id}`]: () => json(kpis({ doctor: daniel })),
    })
    renderRoute('/dashboard')

    expect(await screen.findByRole('heading', { name: 'My day' })).toBeInTheDocument()
    expect(await screen.findByText(/Good (morning|afternoon|evening), Dr\. Brown/)).toBeInTheDocument()
    expect(screen.getByText('Allergy: Penicillin')).toBeInTheDocument()
    expect(screen.getByText('Hypertension')).toBeInTheDocument()
    expect(screen.getByText('42 patients, 30 as primary doctor')).toBeInTheDocument()
    expect(
      within(screen.getByRole('list', { name: 'Recently seen patients' })).getByRole('link', { name: 'Alice Martin' }),
    ).toHaveAttribute('href', `/patients/${alice.id}`)
    expect(
      within(screen.getByRole('group', { name: 'Today' })).getByRole('link', {
        name: /Upcoming \(7 days\)\s*9/,
      }),
    ).toBeInTheDocument()
    expect(await screen.findByRole('group', { name: 'Key figures' })).toBeInTheDocument()
    expect(screen.getByText('No activity yet.')).toBeInTheDocument()

    const paths = requests.map((request) => request.path)
    expect(paths).not.toContain('/dashboard/owner')
    expect(paths).not.toContain('/kpis/clinic')
  })

  it('shows an error with retry when the dashboard cannot load', async () => {
    let fail = true
    fakeApi({
      'GET /auth/me': () => json(testUser('DOCTOR')),
      'GET /dashboard/doctor': () => (fail ? problem(500, 'INTERNAL_ERROR') : json(doctorDashboard)),
      [`GET /kpis/doctors/${daniel.id}`]: () => json(kpis({ doctor: daniel })),
    })
    renderRoute('/dashboard')
    const user = userEvent.setup()

    const retry = await screen.findByRole('button', { name: 'Try again' })
    fail = false
    await user.click(retry)
    expect(await screen.findByText('Allergy: Penicillin')).toBeInTheDocument()
  })

  it('is translated into French', async () => {
    fakeApi({
      'GET /auth/me': () => json(testUser('OWNER', { preferredLanguage: 'fr' })),
      'GET /dashboard/owner': () => json(ownerDashboard),
      'GET /kpis/clinic': () => json(kpis()),
    })
    renderRoute('/dashboard')

    expect(await screen.findByRole('region', { name: 'Salle d’attente' })).toBeInTheDocument()
    expect(screen.getByText('Rien d’autre de prévu aujourd’hui.')).toBeInTheDocument()
  })
})
