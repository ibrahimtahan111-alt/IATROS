import { Tabs as RadixTabs } from 'radix-ui'
import type { ReactNode } from 'react'
import styles from './Tabs.module.css'

export interface TabItem {
  value: string
  label: string
  content: ReactNode
}

interface TabsProps {
  items: readonly TabItem[]
  value: string
  onValueChange: (value: string) => void
  /** Accessible name of the tab list. */
  label: string
}

/** Controlled so the active tab can live in the URL. Arrow keys move between tabs. */
export function Tabs({ items, value, onValueChange, label }: TabsProps) {
  return (
    <RadixTabs.Root value={value} onValueChange={onValueChange} className={styles.root}>
      <RadixTabs.List aria-label={label} className={styles.list}>
        {items.map((item) => (
          <RadixTabs.Trigger key={item.value} value={item.value} className={styles.trigger}>
            {item.label}
          </RadixTabs.Trigger>
        ))}
      </RadixTabs.List>
      {items.map((item) => (
        <RadixTabs.Content key={item.value} value={item.value} className={styles.content}>
          {item.content}
        </RadixTabs.Content>
      ))}
    </RadixTabs.Root>
  )
}
