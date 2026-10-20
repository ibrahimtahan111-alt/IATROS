import { z } from 'zod/mini'

// Schema building blocks. Messages are API field error codes, translated by useFieldErrorMessage, so client and
// server validation read the same. Limits mirror the backend DTOs; the backend remains the authority.

export function requiredText(max: number) {
  return z
    .string()
    .check(
      z.trim(),
      z.minLength(1, { error: 'REQUIRED', abort: true }),
      z.maxLength(max, { error: 'INVALID_LENGTH', abort: true }),
    )
}

/** Trimmed; an empty value is sent as null (see {@link emptyToNull}). */
export function optionalText(max: number) {
  return z.string().check(z.trim(), z.maxLength(max, { error: 'INVALID_LENGTH', abort: true }))
}

export const emailField = z
  .string()
  .check(
    z.trim(),
    z.minLength(1, { error: 'REQUIRED', abort: true }),
    z.maxLength(254, { error: 'INVALID_LENGTH', abort: true }),
    z.regex(z.regexes.email, 'INVALID_EMAIL'),
  )

export const optionalEmailField = z
  .string()
  .check(
    z.trim(),
    z.maxLength(254, { error: 'INVALID_LENGTH', abort: true }),
    z.refine((value) => value === '' || z.regexes.email.test(value), { error: 'INVALID_EMAIL' }),
  )

export function emptyToNull(value: string): string | null {
  return value === '' ? null : value
}
