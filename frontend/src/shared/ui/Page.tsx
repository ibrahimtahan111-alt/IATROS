import { useId, type FormEvent, type ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './Page.module.css'

/**
 * Workspace page body (design-system.md §4.3). Forms and records use the readable width; tables and calendars use
 * the full width.
 */
export function Page({ width = 'readable', children }: { width?: 'readable' | 'full'; children: ReactNode }) {
  return <div className={cx(styles.page, width === 'readable' && styles.readable)}>{children}</div>
}

interface SectionProps {
  title: string
  description?: string
  /** Section-level action (e.g. "Edit"), shown at the end of the title row. */
  action?: ReactNode
  children: ReactNode
}

/** Pages are built from titled sections separated by whitespace and a hairline, not stacked cards. */
export function Section({ title, description, action, children }: SectionProps) {
  const titleId = useId()
  return (
    <section className={styles.section} aria-labelledby={titleId}>
      <div className={styles.sectionHeader}>
        <div>
          <h2 id={titleId} className={styles.sectionTitle}>
            {title}
          </h2>
          {description && <p className={styles.sectionDescription}>{description}</p>}
        </div>
        {action && <div className={styles.sectionAction}>{action}</div>}
      </div>
      {children}
    </section>
  )
}

export interface DetailItem {
  label: string
  value: ReactNode
}

/** Read-only label/value pairs laid out in a responsive grid. */
export function DetailList({ items }: { items: readonly DetailItem[] }) {
  return (
    <dl className={styles.details}>
      {items.map((item) => (
        <div key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}

/** Vertical form layout with consistent spacing and a footer row for the submit action. */
export function FormStack({ children, onSubmit }: { children: ReactNode; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <form className={styles.form} onSubmit={onSubmit} noValidate>
      {children}
    </form>
  )
}

export function FormRow({ children }: { children: ReactNode }) {
  return <div className={styles.formRow}>{children}</div>
}

export function FormActions({ children }: { children: ReactNode }) {
  return <div className={styles.formActions}>{children}</div>
}
