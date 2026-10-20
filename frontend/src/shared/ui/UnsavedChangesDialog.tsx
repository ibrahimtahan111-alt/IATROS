import type { Blocker } from 'react-router'
import { useTranslation } from 'react-i18next'
import { ConfirmDialog } from './ConfirmDialog'

/** Confirmation for a navigation held by useUnsavedChangesGuard. */
export function UnsavedChangesDialog({ blocker }: { blocker: Blocker }) {
  const { t } = useTranslation()

  return (
    <ConfirmDialog
      open={blocker.state === 'blocked'}
      onOpenChange={(open) => !open && blocker.reset?.()}
      title={t('unsavedChanges.title')}
      consequence={t('unsavedChanges.consequence')}
      confirmLabel={t('unsavedChanges.discard')}
      onConfirm={() => blocker.proceed?.()}
      destructive
    />
  )
}
