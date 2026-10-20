import { Lock } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'
import { homePathFor } from '../../app/navigation'
import { AppLoading } from '../../layouts/AppLoading'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { useCurrentUser, useSignedOut, type Role } from './api'
import { safeRedirectPath, useSessionUser } from './session'

/** Renders child routes only with a valid session; otherwise sends the user to sign in and back afterwards. */
export function RequireAuth() {
  const location = useLocation()
  const { data: user, isPending, error, refetch, isRefetching } = useCurrentUser()
  const signedOut = useSignedOut()

  if (isPending) {
    return <AppLoading />
  }
  if (error) {
    return <ErrorState error={error} onRetry={() => void refetch()} retrying={isRefetching} />
  }
  if (!user) {
    const from = `${location.pathname}${location.search}`
    return <Navigate to="/login" replace state={signedOut ? null : { from }} />
  }
  return <Outlet />
}

/**
 * Hides modules the role cannot use (ui-ux.md §45). This is a usability layer only: the backend authorizes every
 * request on its own.
 */
export function RequireRole({ roles }: { roles: readonly Role[] }) {
  const { t } = useTranslation()
  const user = useSessionUser()

  if (!roles.includes(user.role)) {
    return <EmptyState icon={<Lock />} title={t('noAccess.title')} description={t('noAccess.description')} />
  }
  return <Outlet />
}

/**
 * Sign-in and password pages are for signed-out users. Signed-in users (including right after signing in) go to the
 * page that sent them to sign in, or else to their home page.
 */
export function RedirectIfAuthenticated() {
  const location = useLocation()
  const { data: user, isPending } = useCurrentUser()

  if (isPending) {
    return <AppLoading />
  }
  if (user) {
    const from = safeRedirectPath((location.state as { from?: unknown } | null)?.from)
    return <Navigate to={from ?? homePathFor(user.role)} replace />
  }
  return <Outlet />
}

/** "/" sends each role to its landing page (ui-ux.md §38). */
export function HomeRedirect() {
  const user = useSessionUser()
  return <Navigate to={homePathFor(user.role)} replace />
}
