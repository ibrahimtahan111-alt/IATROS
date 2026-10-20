import type { FieldValues, Path, UseFormSetError, UseFormSetFocus } from 'react-hook-form'
import { isApiError } from './ApiError'

/** True when the error carries field errors that the form shows next to its fields. */
export function hasFieldErrors(error: unknown): boolean {
  return isApiError(error) && error.fieldErrors.length > 0
}

/**
 * Shows server field errors next to their fields and focuses the first one (design-system.md §11.2). Field names
 * use the API's dotted paths (e.g. `doctor.primarySpecialty`), which match react-hook-form paths; `fieldMap` renames
 * API fields for forms that split them (e.g. an instant entered as a date and a time).
 */
export function applyFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  setFocus: UseFormSetFocus<T>,
  fieldMap: Readonly<Record<string, string>> = {},
): void {
  if (!isApiError(error) || error.fieldErrors.length === 0) {
    return
  }
  const formField = (field: string) => (fieldMap[field] ?? field) as Path<T>
  for (const { field, code } of error.fieldErrors) {
    setError(formField(field), { message: code })
  }
  setFocus(formField(error.fieldErrors[0]!.field))
}
