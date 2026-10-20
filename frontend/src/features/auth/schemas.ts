import { z } from 'zod/mini'
import { emailField as email } from '../../shared/forms/fields'

/** Mirrors the backend policy (decisions.md D-013); the backend remains the authority. */
export const PASSWORD_MIN_LENGTH = 12
export const PASSWORD_MAX_LENGTH = 128

// zod/mini keeps the sign-in route within its bundle budget (design-system.md §17).
// Messages are error codes, translated by useFieldErrorMessage. The first failing check per field is shown.
const required = () => z.string().check(z.minLength(1, { error: 'REQUIRED', abort: true }))

const newPassword = z
  .string()
  .check(z.minLength(PASSWORD_MIN_LENGTH, 'PASSWORD_TOO_SHORT'), z.maxLength(PASSWORD_MAX_LENGTH, 'PASSWORD_TOO_LONG'))

const passwordsMatch = z.refine<{ newPassword: string; confirmPassword: string }>(
  (values) => values.newPassword === values.confirmPassword,
  { path: ['confirmPassword'], error: 'PASSWORD_MISMATCH' },
)

export const loginSchema = z.object({
  email,
  password: required(),
})

export const forgotPasswordSchema = z.object({ email })

export const setPasswordSchema = z
  .object({
    newPassword,
    confirmPassword: required(),
  })
  .check(passwordsMatch)

export const changePasswordSchema = z
  .object({
    currentPassword: required(),
    newPassword,
    confirmPassword: required(),
  })
  .check(passwordsMatch)

export type LoginValues = z.infer<typeof loginSchema>
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>
export type SetPasswordValues = z.infer<typeof setPasswordSchema>
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>
