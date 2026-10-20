import { Tooltip as RadixTooltip } from 'radix-ui'
import type { ReactElement, ReactNode } from 'react'
import styles from './Tooltip.module.css'

interface TooltipProps {
  content: ReactNode
  side?: 'top' | 'right' | 'bottom' | 'left'
  children: ReactElement
}

/** Supplementary hint on hover and focus. Never the only place where information appears. */
export function Tooltip({ content, side = 'top', children }: TooltipProps) {
  return (
    <RadixTooltip.Root>
      <RadixTooltip.Trigger asChild>{children}</RadixTooltip.Trigger>
      <RadixTooltip.Portal>
        <RadixTooltip.Content className={styles.content} side={side} sideOffset={6}>
          {content}
        </RadixTooltip.Content>
      </RadixTooltip.Portal>
    </RadixTooltip.Root>
  )
}

export const TooltipProvider = RadixTooltip.Provider
