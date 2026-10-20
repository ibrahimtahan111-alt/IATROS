import { Toast as RadixToast } from 'radix-ui'
import { CircleCheck, CircleX, Info, X } from 'lucide-react'
import { useCallback, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cx } from '../lib/cx'
import styles from './Toast.module.css'
import { ToastContext, type ShowToast, type ToastTone } from './useToast'

interface ToastMessage {
  id: number
  tone: ToastTone
  message: string
}

const ICONS = { success: CircleCheck, info: Info, danger: CircleX } as const
const DURATION_MS = 4000

let nextId = 0

/**
 * Confirms completed actions (bottom end, 4 s, dismissible; design-system.md §11.9). Toasts never carry information
 * the user must act on; use an inline alert for that.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [toasts, setToasts] = useState<ToastMessage[]>([])

  const show = useCallback<ShowToast>((message, tone = 'success') => {
    nextId += 1
    const id = nextId
    setToasts((current) => [...current, { id, tone, message }])
  }, [])

  const remove = (id: number) => setToasts((current) => current.filter((toast) => toast.id !== id))

  return (
    <ToastContext.Provider value={show}>
      <RadixToast.Provider duration={DURATION_MS} label={t('notifications')}>
        {children}
        {toasts.map((toast) => {
          const Icon = ICONS[toast.tone]
          return (
            <RadixToast.Root
              key={toast.id}
              className={cx(styles.toast, styles[toast.tone])}
              type={toast.tone === 'danger' ? 'foreground' : 'background'}
              onOpenChange={(open) => !open && remove(toast.id)}
            >
              <Icon className={styles.icon} aria-hidden="true" />
              <RadixToast.Description className={styles.message}>{toast.message}</RadixToast.Description>
              <RadixToast.Close className={styles.close} aria-label={t('actions.close')}>
                <X aria-hidden="true" />
              </RadixToast.Close>
            </RadixToast.Root>
          )
        })}
        <RadixToast.Viewport className={styles.viewport} />
      </RadixToast.Provider>
    </ToastContext.Provider>
  )
}