import { useCallback, useState } from 'react'
import { applyThemePreference, readThemePreference, type ThemePreference } from './theme'

export function useThemePreference(): [ThemePreference, (preference: ThemePreference) => void] {
  const [preference, setPreference] = useState<ThemePreference>(readThemePreference)

  const change = useCallback((next: ThemePreference) => {
    applyThemePreference(next)
    setPreference(next)
  }, [])

  return [preference, change]
}
