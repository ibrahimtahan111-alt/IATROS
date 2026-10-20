import { Dialog as RadixDialog } from 'radix-ui'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { IconButton } from './IconButton'
import styles from './Dialog.module.css'

interface DialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children?: ReactNode
  /** Action buttons, end-aligned. The primary action comes last. */
  footer?: ReactNode
}

/**
 * Short focused forms and confirmations (design-system.md §4.5). Focus is trapped while open and returns to the
 * opening element on close.
 */
export function Dialog({ open, onOpenChange, title, description, children, footer }: DialogProps) {
  const { t } = useTranslation()

  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className={styles.overlay} />
        <RadixDialog.Content className={styles.content}>
          <header className={styles.header}>
            <RadixDialog.Title className={styles.title}>{title}</RadixDialog.Title>
            <RadixDialog.Close asChild>
              <IconButton label={t('actions.close')} icon={<X />} />
            </RadixDialog.Close>
          </header>
          {description ? (
            <RadixDialog.Description className={styles.description}>{description}</RadixDialog.Description>
          ) : (
            <RadixDialog.Description className="visually-hidden">{title}</RadixDialog.Description>
          )}
          {children && <div className={styles.body}>{children}</div>}
          {footer && <footer className={styles.footer}>{footer}</footer>}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  )
}
