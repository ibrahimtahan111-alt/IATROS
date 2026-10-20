import { useEffect } from 'react'
import { useBlocker } from 'react-router'

interface GuardOptions {
  /** Search parameters that select what the form edits (e.g. `doctor`); changing one also leaves the form. */
  searchParams?: readonly string[]
}

/**
 * Warns before leaving a form with unsaved changes (design-system.md §11.2): in-app navigation is held until the
 * user confirms (render {@link UnsavedChangesDialog} with the returned blocker), and closing or reloading the tab
 * triggers the browser's own prompt.
 */
export function useUnsavedChangesGuard(dirty: boolean, { searchParams = [] }: GuardOptions = {}) {
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (!dirty) return false
    if (currentLocation.pathname !== nextLocation.pathname) return true
    const current = new URLSearchParams(currentLocation.search)
    const next = new URLSearchParams(nextLocation.search)
    return searchParams.some((name) => current.get(name) !== next.get(name))
  })

  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  return blocker
}
