import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { cx } from '../lib/cx'
import styles from './KpiTile.module.css'

export interface KpiComparison {
  /** Already formatted, e.g. "+12% vs previous period". */
  text: string
  direction: 'up' | 'down' | 'flat'
  /** Whether the change is good news; colours the comparison, never the only cue (sign and icon also show it). */
  tone: 'positive' | 'negative' | 'neutral'
}

interface KpiTileProps {
  label: string
  value: string
  comparison?: KpiComparison
  /** Detailed view, when one exists. */
  to?: string
}

const ICONS = { up: ArrowUpRight, down: ArrowDownRight, flat: Minus }

/** Large tabular number, short label, and optional comparison (design-system.md §11.8). */
export function KpiTile({ label, value, comparison, to }: KpiTileProps) {
  const Icon = comparison ? ICONS[comparison.direction] : null
  const body = (
    <>
      <span className={styles.label}>{label}</span>
      <span className={cx(styles.value, 'tabular')}>{value}</span>
      {comparison && Icon && (
        <span className={cx(styles.comparison, styles[comparison.tone])}>
          <Icon aria-hidden="true" className={styles.icon} />
          {comparison.text}
        </span>
      )}
    </>
  )
  return to ? (
    <Link to={to} className={cx(styles.tile, styles.link)}>
      {body}
    </Link>
  ) : (
    <div className={styles.tile}>{body}</div>
  )
}

/** A responsive row of KPI tiles. */
export function KpiGrid({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.grid} role="group" aria-label={label}>
      {children}
    </div>
  )
}
