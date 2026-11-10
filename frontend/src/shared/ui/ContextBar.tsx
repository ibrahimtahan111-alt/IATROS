import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { ButtonLink } from './ButtonLink'
import styles from './ContextBar.module.css'

interface ContextBarProps {
  title: string
  /** A return link shown above the title. */
  back?: { to: string; label: string }
  /** Scope controls: date navigation, doctor selector, view switch. */
  scope?: ReactNode
  /** The page's single primary action. */
  action?: ReactNode
}

/** Top of every workspace page: title · scope · primary action (design-system.md §4.2). Also sets the tab title. */
export function ContextBar({ title, back, scope, action }: ContextBarProps) {
  useDocumentTitle(title)

  return (
    <header className={styles.header}>
      {back && (
        <ButtonLink to={back.to} variant="ghost" size="sm" icon={<ArrowLeft aria-hidden="true" />}>
          {back.label}
        </ButtonLink>
      )}
      <div className={styles.bar}>
        <h1 className={styles.title}>{title}</h1>
        {scope && <div className={styles.scope}>{scope}</div>}
        {action && <div className={styles.action}>{action}</div>}
      </div>
    </header>
  )
}
