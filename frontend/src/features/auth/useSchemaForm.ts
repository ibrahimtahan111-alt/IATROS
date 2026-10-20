import { useState, type FormEvent } from 'react'
import type { z } from 'zod/mini'

type FieldErrors<T> = Partial<Record<keyof T & string, string>>

function fieldErrorsOf<T>(issues: readonly { path: readonly PropertyKey[]; message: string }[]): FieldErrors<T> {
  const errors: Record<string, string> = {}
  for (const issue of issues) {
    const field = String(issue.path[0] ?? '')
    errors[field] ??= issue.message
  }
  return errors as FieldErrors<T>
}

function valuesOf(form: HTMLFormElement): Record<string, string> {
  const values: Record<string, string> = {}
  new FormData(form).forEach((value, key) => {
    if (typeof value === 'string') values[key] = value
  })
  return values
}

/**
 * Minimal form handling for the pre-sign-in pages, which must stay within the login bundle budget
 * (design-system.md §17). Inputs are uncontrolled and identified by `name`; other forms use react-hook-form.
 * Errors are error codes for useFieldErrorMessage; a field's error is re-checked as the user corrects it.
 */
export function useSchemaForm<S extends z.ZodMiniType<Record<string, unknown>>>(schema: S) {
  type Values = z.output<S>
  const [errors, setErrors] = useState<FieldErrors<Values>>({})

  const handleSubmit = (submit: (values: Values) => void) => (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = event.currentTarget
    const result = schema.safeParse(valuesOf(form))
    if (result.success) {
      setErrors({})
      submit(result.data)
      return
    }
    const nextErrors = fieldErrorsOf<Values>(result.error.issues)
    setErrors(nextErrors)
    const firstInvalid = Object.keys(nextErrors)[0]
    const element = firstInvalid ? form.elements.namedItem(firstInvalid) : null
    if (element instanceof HTMLElement) element.focus()
  }

  const handleChange = (event: FormEvent<HTMLFormElement>) => {
    const name = (event.target as HTMLInputElement).name as keyof Values & string
    if (!errors[name]) return
    const result = schema.safeParse(valuesOf(event.currentTarget))
    const stillInvalid = result.success ? undefined : fieldErrorsOf<Values>(result.error.issues)[name]
    setErrors((current) => ({ ...current, [name]: stillInvalid }))
  }

  return { errors, handleSubmit, handleChange }
}
