import { RotateCcw, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../api/errorMessage'
import { Button } from './Button'
import { EmptyState } from './EmptyState'

interface ErrorStateProps {
  error: unknown
  onRetry?: () => void
  retrying?: boolean
}

/** Shown in place of content that could not load, with a retry (design-system.md §11.9). */
export function ErrorState({ error, onRetry, retrying = false }: ErrorStateProps) {
  const { t } = useTranslation()
  const message = useErrorMessage()

  return (
    <div role="alert">
      <EmptyState
        icon={<TriangleAlert />}
        title={t('states.loadFailed')}
        description={message(error)}
        action={
          onRetry && (
            <Button icon={<RotateCcw />} onClick={onRetry} loading={retrying}>
              {t('actions.retry')}
            </Button>
          )
        }
      />
    </div>
  )
}
