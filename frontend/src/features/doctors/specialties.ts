import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { apiRequest } from '../../shared/api/http'
import type { SelectOption } from '../../shared/ui/Select'

/** GET /api/v1/specialties (api.md §8). The code is the stable identifier; labels come from translations. */
export interface Specialty {
  id: string
  code: string
}

/** The catalog changes only with a release, so it is fetched once per session. */
export function useSpecialties() {
  return useQuery({
    queryKey: ['specialties'],
    queryFn: () => apiRequest<Specialty[]>('/specialties'),
    staleTime: Infinity,
  })
}

/** Translated specialty name; requires the `specialties` namespace (loaded by the route). */
export function useSpecialtyLabel(): (code: string | null | undefined) => string {
  const { t, i18n } = useTranslation('specialties')
  return (code) => {
    if (!code) return ''
    return i18n.exists(`specialties:${code}`) ? t(code as 'OTHER') : code
  }
}

export function useSpecialtyOptions(): SelectOption[] {
  const { data } = useSpecialties()
  const label = useSpecialtyLabel()
  return (data ?? [])
    .map((specialty) => ({ value: specialty.code, label: label(specialty.code) }))
    .sort((a, b) => a.label.localeCompare(b.label))
}
