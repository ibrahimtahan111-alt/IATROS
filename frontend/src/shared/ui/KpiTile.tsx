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

/** Same status tones as the overview pulse, so a period reads as the same board. */
export type KpiTone = 'scheduled' | 'waiting' | 'consultation' | 'completed' | 'cancelled' | 'missed' | 'accent'

interface KpiTileProps {
  label: string
  value: string
  comparison?: KpiComparison
  /** Soft status fill and a leading edge. Omitted tiles stay on the plain surface. */
  tone?: KpiTone
  /** Detailed view, when one exists. */
  to?: string
}

const ICONS = { up: ArrowUpRight, down: ArrowDownRight, flat: Minus }

/** Large tabular number, short label, and optional comparison (design-system.md §11.8). */
export function KpiTile({ label, value, comparison, tone, to }: KpiTileProps) {
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
  const className = cx(styles.tile, tone && styles[tone], to && styles.link)
  return to ? (
    <Link to={to} className={className}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
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
