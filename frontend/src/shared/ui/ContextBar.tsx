import type { ReactNode } from 'react'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import styles from './ContextBar.module.css'

interface ContextBarProps {
  title: string
  /** Scope controls: date navigation, doctor selector, view switch. */
  scope?: ReactNode
  /** The page's single primary action. */
  action?: ReactNode
}

/** Top of every workspace page: title · scope · primary action (design-system.md §4.2). Also sets the tab title. */
export function ContextBar({ title, scope, action }: ContextBarProps) {
  useDocumentTitle(title)

  return (
    <header className={styles.bar}>
      <h1 className={styles.title}>{title}</h1>
      {scope && <div className={styles.scope}>{scope}</div>}
      {action && <div className={styles.action}>{action}</div>}
    </header>
  )
}
