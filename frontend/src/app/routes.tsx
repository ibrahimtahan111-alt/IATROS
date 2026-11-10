import type { RouteObject } from 'react-router'
import { HomeRedirect, RedirectIfAuthenticated, RequireAuth, RequireRole } from '../features/auth/guards'
import { AppLoading } from '../layouts/AppLoading'
import { AuthLayout } from '../layouts/AuthLayout'
import { lazyPage } from './lazyPage'
import { NAV_ITEMS, type NavItem } from './navigation'
import { NotFoundPage } from './pages/NotFoundPage'

const CLINICAL_ROLES = ['OWNER', 'DOCTOR'] as const

/** Modules that have been built; the others show a placeholder until their phase lands. */
const MODULE_ROUTES: Partial<Record<string, RouteObject[]>> = {
  '/dashboard': [
    {
      index: true,
      lazy: lazyPage(async () => (await import('../features/dashboard/DashboardPage')).DashboardPage, [
        'dashboard',
        'activity',
        'calendar',
      ]),
    },
  ],
  '/activity': [
    { index: true, lazy: lazyPage(async () => (await import('../features/activity/ActivityPage')).ActivityPage, ['activity']) },
  ],
  '/patients': [
    {
      // The list stays visible behind the add dialog.
      lazy: lazyPage(async () => (await import('../features/patients/PatientsPage')).PatientsPage, ['patients']),
      children: [
        { index: true },
        {
          path: 'new',
          lazy: lazyPage(async () => (await import('../features/patients/NewPatientDialog')).NewPatientDialog, ['patients']),
        },
      ],
    },
    {
      path: ':patientId',
      lazy: lazyPage(async () => (await import('../features/patients/PatientPage')).PatientPage, ['patients', 'calendar']),
    },
    {
      path: ':patientId/edit',
      lazy: lazyPage(async () => (await import('../features/patients/EditPatientPage')).EditPatientPage, ['patients']),
    },
    {
      // Clinical data: never for secretaries (D-037). The server enforces it independently.
      element: <RequireRole roles={CLINICAL_ROLES} />,
      children: [
        {
          path: ':patientId/medical-record',
          lazy: lazyPage(
            async () => (await import('../features/medical-records/MedicalRecordPage')).MedicalRecordPage,
            ['medicalRecords'],
          ),
        },
        {
          path: ':patientId/consultations/new',
          lazy: lazyPage(
            async () => (await import('../features/medical-records/NewConsultationPage')).NewConsultationPage,
            ['medicalRecords'],
          ),
        },
        {
          path: ':patientId/consultations/:consultationId',
          lazy: lazyPage(
            async () => (await import('../features/medical-records/ConsultationPage')).ConsultationPage,
            ['medicalRecords'],
          ),
        },
        {
          path: ':patientId/consultations/:consultationId/edit',
          lazy: lazyPage(
            async () => (await import('../features/medical-records/EditConsultationPage')).EditConsultationPage,
            ['medicalRecords'],
          ),
        },
      ],
    },
  ],
  '/calendar': [
    { index: true, lazy: lazyPage(async () => (await import('../features/calendar/CalendarPage')).CalendarPage, ['calendar']) },
  ],
  '/team': [
    { index: true, lazy: lazyPage(async () => (await import('../features/team/TeamPage')).TeamPage, ['team', 'specialties']) },
    {
      path: 'new',
      lazy: lazyPage(async () => (await import('../features/team/NewMemberPage')).NewMemberPage, ['team', 'specialties']),
    },
    {
      path: ':memberId',
      lazy: lazyPage(async () => (await import('../features/team/MemberPage')).MemberPage, ['team', 'doctors', 'specialties']),
    },
  ],
  '/schedules': [
    { index: true, lazy: lazyPage(async () => (await import('../features/schedules/SchedulesPage')).SchedulesPage, ['schedules']) },
  ],
  '/settings': [
    { index: true, lazy: lazyPage(async () => (await import('../features/clinic/SettingsPage')).SettingsPage, ['clinic']) },
  ],
}

function moduleRoutes(item: NavItem): RouteObject[] {
  return (
    MODULE_ROUTES[item.path] ?? [
      {
        index: true,
        lazy: async () => {
          const { ModulePage } = await import('./pages/ModulePage')
          return { Component: () => <ModulePage path={item.path} /> }
        },
      },
    ]
  )
}

/** Canonical routes (ui-ux.md §45). Feature pages are code-split; only sign-in ships in the initial bundle. */
export const routes: RouteObject[] = [
  {
    HydrateFallback: AppLoading,
    children: [
      {
        element: <AuthLayout />,
        children: [
          {
            element: <RedirectIfAuthenticated />,
            children: [
              { path: '/login', lazy: async () => ({ Component: (await import('../features/auth/LoginPage')).LoginPage }) },
              {
                path: '/forgot-password',
                lazy: async () => ({ Component: (await import('../features/auth/ForgotPasswordPage')).ForgotPasswordPage }),
              },
            ],
          },
          {
            path: '/reset-password',
            lazy: async () => ({ Component: (await import('../features/auth/SetPasswordPage')).ResetPasswordPage }),
          },
          {
            path: '/accept-invitation',
            lazy: async () => ({ Component: (await import('../features/auth/SetPasswordPage')).AcceptInvitationPage }),
          },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            lazy: async () => ({ Component: (await import('../layouts/AppShell')).AppShell }),
            children: [
              { index: true, element: <HomeRedirect /> },
              ...NAV_ITEMS.map(
                (item): RouteObject => ({
                  path: item.path,
                  element: <RequireRole roles={item.roles} />,
                  children: moduleRoutes(item),
                }),
              ),
              {
                path: '/account',
                lazy: lazyPage(async () => (await import('../features/account/AccountPage')).AccountPage, [
                  'account',
                  'doctors',
                  'specialties',
                ]),
              },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
