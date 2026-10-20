import type { CSSProperties, ReactNode } from 'react'
import { cx } from '../lib/cx'
import styles from './Skeleton.module.css'

interface SkeletonProps {
  width?: CSSProperties['width']
  height?: CSSProperties['height']
  className?: string
}

/** Placeholder that mirrors the final layout while content loads (design-system.md §11.10). */
export function Skeleton({ width = '100%', height = '1em', className }: SkeletonProps) {
  return <span className={cx(styles.skeleton, className)} style={{ width, height }} aria-hidden="true" />
}

/** Announces loading to assistive technology while skeletons are shown. */
export function LoadingRegion({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="visually-hidden">{label}</span>
      {children}
    </div>
  )
}
