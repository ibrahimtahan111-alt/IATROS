import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { Dialog } from './Dialog'

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  /** States the consequence of confirming (design-system.md §11.9). */
  consequence: string
  confirmLabel: string
  onConfirm: () => void
  destructive?: boolean
  pending?: boolean
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  consequence,
  confirmLabel,
  onConfirm,
  destructive = false,
  pending = false,
}: ConfirmDialogProps) {
  const { t } = useTranslation()

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !pending && onOpenChange(next)}
      title={title}
      description={consequence}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
            {t('actions.cancel')}
          </Button>
          <Button variant={destructive ? 'danger' : 'primary'} onClick={onConfirm} loading={pending}>
            {confirmLabel}
          </Button>
        </>
      }
    />
  )
}
