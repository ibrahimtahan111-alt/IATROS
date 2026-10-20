import { Dialog as RadixDialog } from 'radix-ui'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { IconButton } from './IconButton'
import styles from './ContextPanel.module.css'

interface ContextPanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  /** Shown under the title, e.g. a status. */
  subtitle?: ReactNode
  children: ReactNode
  /** Quick actions, pinned to the bottom of the panel. */
  footer?: ReactNode
}

/**
 * Right-side panel for viewing a resource and its quick actions without losing place (design-system.md §4.4).
 * Focus is trapped while open and returns to the opening element on close.
 */
export function ContextPanel({ open, onOpenChange, title, subtitle, children, footer }: ContextPanelProps) {
  const { t } = useTranslation()

  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className={styles.overlay} />
        <RadixDialog.Content className={styles.panel}>
          <header className={styles.header}>
            <div className={styles.heading}>
              <RadixDialog.Title className={styles.title}>{title}</RadixDialog.Title>
              {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
            </div>
            <RadixDialog.Close asChild>
              <IconButton label={t('actions.close')} icon={<X />} />
            </RadixDialog.Close>
          </header>
          <RadixDialog.Description className="visually-hidden">{title}</RadixDialog.Description>
          <div className={styles.body}>{children}</div>
          {footer && <footer className={styles.footer}>{footer}</footer>}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  )
}
