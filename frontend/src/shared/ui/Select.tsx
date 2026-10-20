import { Select as RadixSelect } from 'radix-ui'
import { Check, ChevronDown } from 'lucide-react'
import type { FieldControlProps } from './Field'
import styles from './Select.module.css'

export interface SelectOption {
  value: string
  label: string
}

interface SelectProps extends Partial<FieldControlProps> {
  /** For selects without a visible label, such as toolbar filters. */
  'aria-label'?: string
  'aria-labelledby'?: string
  value: string | undefined
  onValueChange: (value: string) => void
  options: readonly SelectOption[]
  placeholder?: string
  disabled?: boolean
  name?: string
}

export function Select({ value, onValueChange, options, placeholder, disabled, name, ...control }: SelectProps) {
  return (
    <RadixSelect.Root value={value} onValueChange={onValueChange} disabled={disabled} name={name}>
      <RadixSelect.Trigger className={styles.trigger} {...control}>
        <RadixSelect.Value placeholder={placeholder} />
        <RadixSelect.Icon className={styles.icon}>
          <ChevronDown />
        </RadixSelect.Icon>
      </RadixSelect.Trigger>
      <RadixSelect.Portal>
        <RadixSelect.Content className={styles.content} position="popper" sideOffset={4}>
          <RadixSelect.Viewport className={styles.viewport}>
            {options.map((option) => (
              <RadixSelect.Item key={option.value} value={option.value} className={styles.item}>
                <RadixSelect.ItemText>{option.label}</RadixSelect.ItemText>
                <RadixSelect.ItemIndicator className={styles.indicator}>
                  <Check />
                </RadixSelect.ItemIndicator>
              </RadixSelect.Item>
            ))}
          </RadixSelect.Viewport>
        </RadixSelect.Content>
      </RadixSelect.Portal>
    </RadixSelect.Root>
  )
}
