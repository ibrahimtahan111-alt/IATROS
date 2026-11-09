import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeApi, json, testUser } from '../../test/fakeApi'
import { renderRoute } from '../../test/renderRoute'
import type { ActivityItem } from './api'
import { activityItem, chloe, daniel, kpis } from './fixtures'

const page = (content: ActivityItem[]) =>
  json({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
  })

const clinicKpis = kpis({
  doctors: [
    {
      doctor: daniel,
      appointments: 25,
      completed: 18,
      cancelled: 3,
      noShows: 1,
      completionRate: 0.82,
      consultations: 17,
    },
    {
      doctor: chloe,
      appointments: 15,
      completed: 10,
      cancelled: 1,
      noShows: 1,
      completionRate: 0.83,
      consultations: 10,
    },
  ],
})

describe('activity and KPIs', () => {
  it('shows owners clinic KPIs, the doctor breakdown, and the clinic feed', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('OWNER')),
      'GET /kpis/clinic': () => json(clinicKpis),
      [`GET /kpis/doctors/${chloe.id}`]: () => json(kpis({ doctor: chloe })),
      'GET /activity': () =>
        page([
          activityItem(),
          activityItem({
            id: 'e2',
            action: 'MEDICAL_ENTRY_CREATED',
            category: 'CLINICAL',
            entityType: 'ALLERGY',
            actor: chloe,
            doctor: null,
            appointmentId: null,
          }),
        ]),
      'GET /doctors': () =>
        json({
          content: [daniel, chloe].map((doctor) => ({
            ...doctor,
            status: 'ACTIVE',
          })),
          page: 0,
          size: 100,
          totalElements: 2,
          totalPages: 1,
        }),
    })
    const { router } = renderRoute('/activity')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Activity', level: 1 })).toBeInTheDocument()
    const switcher = screen.getByRole('group', { name: 'Overview or activity' })
    expect(within(switcher).getByRole('link', { name: 'Overview' })).toHaveAttribute('href', '/dashboard')
    expect(within(switcher).getByRole('link', { name: 'Activity' })).toHaveAttribute('aria-current', 'page')
    const figures = await screen.findByRole('group', { name: 'Key figures' })
    expect(within(figures).getByText('Cancellation rate')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Figures per doctor' })).toBeInTheDocument()
    expect(screen.getByText('Chloe Carter added an allergy to the record of Alice Martin')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Chloe Carter' }))
    await waitFor(() => expect(router.state.location.search).toBe(`?doctor=${chloe.id}`))
    expect(
      await screen.findByRole('heading', {
        name: 'Key figures for Chloe Carter',
      }),
    ).toBeInTheDocument()
    expect(requests.some((request) => request.path === '/activity' && request.query.get('doctorId') === chloe.id)).toBe(
      true,
    )
  })

  it('filters the feed by type and keeps the period in the URL', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('OWNER')),
      'GET /kpis/clinic': () => json(clinicKpis),
      'GET /activity': () => page([activityItem()]),
      'GET /doctors': () =>
        json({
          content: [],
          page: 0,
          size: 100,
          totalElements: 0,
          totalPages: 0,
        }),
    })
    const { router } = renderRoute('/activity')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: '7 days' }))
    await waitFor(() => expect(router.state.location.search).toBe('?period=week'))
    await user.click(screen.getByRole('combobox', { name: 'Activity type' }))
    await user.click(await screen.findByRole('option', { name: 'Medical records' }))

    await waitFor(() =>
      expect(requests.some((request) => request.path === '/activity' && request.query.get('type') === 'CLINICAL')).toBe(
        true,
      ),
    )
    const weekRequest = requests.filter((request) => request.path === '/kpis/clinic').at(-1)!
    const from = new Date(`${weekRequest.query.get('from')}T00:00:00Z`).getTime()
    const to = new Date(`${weekRequest.query.get('to')}T00:00:00Z`).getTime()
    expect((to - from) / 86_400_000).toBe(6)
  })

  it('shows doctors only their own KPIs and activity, without a doctor filter', async () => {
    const { requests } = fakeApi({
      'GET /auth/me': () => json(testUser('DOCTOR')),
      [`GET /kpis/doctors/${daniel.id}`]: () => json(kpis({ doctor: daniel })),
      'GET /activity': () => page([]),
    })
    renderRoute('/activity')

    expect(await screen.findByRole('heading', { name: 'My activity', level: 1 })).toBeInTheDocument()
    expect(
      await screen.findByRole('heading', {
        name: 'Key figures for Daniel Brown',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('No activity')).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Doctor' })).not.toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Figures per doctor' })).not.toBeInTheDocument()

    const paths = requests.map((request) => request.path)
    expect(paths).not.toContain('/kpis/clinic')
    expect(paths).not.toContain('/doctors')
    expect(requests.find((request) => request.path === '/activity')?.query.get('doctorId')).toBeNull()
  })
})
