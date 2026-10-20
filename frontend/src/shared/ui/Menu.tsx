import { DropdownMenu } from 'radix-ui'
import { Check } from 'lucide-react'
import type { ReactElement, ReactNode } from 'react'
import styles from './Menu.module.css'

interface MenuProps {
  trigger: ReactElement
  children: ReactNode
  side?: 'top' | 'right' | 'bottom' | 'left'
  align?: 'start' | 'center' | 'end'
}

/** Overflow and user menus. Fully keyboard operable; focus returns to the trigger on close. */
export function Menu({ trigger, children, side = 'bottom', align = 'end' }: MenuProps) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>{trigger}</DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content className={styles.content} side={side} align={align} sideOffset={6}>
          {children}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

interface MenuItemProps {
  icon?: ReactNode
  children: ReactNode
  onSelect: () => void
  destructive?: boolean
}

export function MenuItem({ icon, children, onSelect, destructive = false }: MenuItemProps) {
  return (
    <DropdownMenu.Item className={styles.item} data-destructive={destructive || undefined} onSelect={onSelect}>
      {icon}
      {children}
    </DropdownMenu.Item>
  )
}

export function MenuLabel({ children }: { children: ReactNode }) {
  return <DropdownMenu.Label className={styles.label}>{children}</DropdownMenu.Label>
}

export function MenuSeparator() {
  return <DropdownMenu.Separator className={styles.separator} />
}

interface MenuRadioGroupProps {
  label: string
  value: string
  onValueChange: (value: string) => void
  options: readonly { value: string; label: string }[]
}

export function MenuRadioGroup({ label, value, onValueChange, options }: MenuRadioGroupProps) {
  return (
    <DropdownMenu.Group>
      <MenuLabel>{label}</MenuLabel>
      <DropdownMenu.RadioGroup value={value} onValueChange={onValueChange}>
        {options.map((option) => (
          <DropdownMenu.RadioItem
            key={option.value}
            value={option.value}
            className={styles.item}
            onSelect={(event) => event.preventDefault()}
          >
            <span className={styles.indicatorSlot}>
              <DropdownMenu.ItemIndicator>
                <Check />
              </DropdownMenu.ItemIndicator>
            </span>
            {option.label}
          </DropdownMenu.RadioItem>
        ))}
      </DropdownMenu.RadioGroup>
    </DropdownMenu.Group>
  )
}
