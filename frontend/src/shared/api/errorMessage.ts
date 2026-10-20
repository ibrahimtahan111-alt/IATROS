import { useTranslation } from 'react-i18next'
import { isApiError } from './ApiError'

/**
 * Translates an API error code into a user-facing message (internationalization.md §20). Unknown codes fall back to
 * a status-based message so users never see technical identifiers.
 */
export function useErrorMessage(): (error: unknown) => string {
  const { t, i18n } = useTranslation('errors')

  return (error: unknown) => {
    if (!isApiError(error)) {
      return t('UNEXPECTED')
    }
    if (i18n.exists(`errors:${error.code}`)) {
      if (error.code === 'RATE_LIMITED' && error.retryAfterSeconds) {
        return t('RATE_LIMITED_WAIT', { minutes: Math.max(1, Math.ceil(error.retryAfterSeconds / 60)) })
      }
      return t(error.code as 'UNEXPECTED')
    }
    if (error.status === 404) {
      return t('NOT_FOUND')
    }
    if (error.status === 409) {
      return t('CONCURRENT_MODIFICATION')
    }
    if (error.status >= 500) {
      return t('INTERNAL_ERROR')
    }
    return t('UNEXPECTED')
  }
}

/** Translates a field error code; field-specific wording wins over the generic one. */
export function useFieldErrorMessage(): (code: string | undefined) => string | undefined {
  const { t, i18n } = useTranslation('errors')
  return (code) => {
    if (!code) {
      return undefined
    }
    return i18n.exists(`errors:field.${code}`) ? t(`field.${code}` as 'field.INVALID') : t('field.INVALID')
  }
}
