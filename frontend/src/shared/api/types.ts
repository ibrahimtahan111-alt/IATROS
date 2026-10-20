/** Paginated list response (api.md §26, decisions.md D-006). `page` is zero-based. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type SortDirection = 'asc' | 'desc'

export interface SortState {
  field: string
  direction: SortDirection
}

export const PAGE_SIZES = [20, 50, 100] as const
