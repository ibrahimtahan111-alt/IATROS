import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { PAGE_SIZES, type SortState } from '../api/types'

export interface ListParams<F extends string> {
  /** Zero-based, as the API expects. The URL shows it one-based. */
  page: number
  size: number
  sort: SortState
  search: string
  filters: Partial<Record<F, string>>
}

export type ListParamsUpdate<F extends string> = Partial<Omit<ListParams<F>, 'filters'>> & {
  filters?: Partial<Record<F, string | null>>
}

const DEFAULT_SIZE = PAGE_SIZES[0]

function parseSort(value: string | null, fallback: SortState): SortState {
  const [field, direction] = value?.split(',') ?? []
  return field && (direction === 'asc' || direction === 'desc') ? { field, direction } : fallback
}

/**
 * Search, filters, sort, and page kept in the URL so they survive refresh and back navigation
 * (design-system.md §11.4). Any change other than the page itself returns to the first page.
 */
export function useListSearchParams<F extends string>(filterKeys: readonly F[], defaultSort: SortState) {
  const [searchParams, setSearchParams] = useSearchParams()

  const params = useMemo<ListParams<F>>(() => {
    const page = Number(searchParams.get('page'))
    const size = Number(searchParams.get('size'))
    const filters: Partial<Record<F, string>> = {}
    for (const key of filterKeys) {
      const value = searchParams.get(key)
      if (value) filters[key] = value
    }
    return {
      page: Number.isInteger(page) && page > 1 ? page - 1 : 0,
      size: (PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_SIZE,
      sort: parseSort(searchParams.get('sort'), defaultSort),
      search: searchParams.get('q') ?? '',
      filters,
    }
  }, [searchParams, filterKeys, defaultSort])

  const update = useCallback(
    (change: ListParamsUpdate<F>) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          const set = (key: string, value: string | null | undefined) => {
            if (value) next.set(key, value)
            else next.delete(key)
          }
          if (change.search !== undefined) set('q', change.search)
          if (change.size !== undefined) set('size', change.size === DEFAULT_SIZE ? null : String(change.size))
          if (change.sort !== undefined) set('sort', `${change.sort.field},${change.sort.direction}`)
          for (const [key, value] of Object.entries(change.filters ?? {}) as [string, string | null][]) {
            set(key, value)
          }
          set('page', change.page ? String(change.page + 1) : null)
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const reset = useCallback(() => setSearchParams(new URLSearchParams(), { replace: true }), [setSearchParams])

  return { params, update, reset }
}

/** Query string for a list endpoint: page, size, sort, search, and non-empty filters. */
export function listQuery<F extends string>(params: ListParams<F>): string {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
    sort: `${params.sort.field},${params.sort.direction}`,
  })
  if (params.search) query.set('search', params.search)
  for (const [key, value] of Object.entries(params.filters) as [string, string | undefined][]) {
    if (value) query.set(key, value)
  }
  return query.toString()
}
