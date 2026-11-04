import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { cx } from '../lib/cx'
import styles from './Logo.module.css'

/**
 * Cross-and-leaf mark from the IATROS brand sheet.
 * The two filled pieces are separated by the leaf's negative-space vein.
 * Keep these paths in sync with public/favicon.svg.
 */
const MARK_A =
  'M34 4.2H47C52.2 4.2 55.4 7.2 55.4 12.2V20.5C47.5 23.2 39.2 28.6 31.6 37.2C26.8 43.6 24.6 48.2 19 48.6H12.4C7.2 48.6 4.4 45.6 4.4 40.4V32.2C4.4 27.2 7.4 24.4 12.6 24.4H26.6V12.4C26.6 7.2 29.6 4.2 34 4.2Z'
const MARK_B =
  'M60.2 24.4H68.6C73.8 24.4 77.2 27.6 77.2 32.6V41.2C77.2 46.4 73.8 49.4 68.6 49.4H56.2V64.6C56.2 70 52.8 73.2 47.4 73.2H36.2C31.2 73.2 28.6 70.4 29.4 66.2C33.2 64.2 39.6 59.2 45.2 51.6C49.2 46 52.4 38.4 54.2 30.2C49.4 34.6 41.2 46.4 33.6 57.2C28.2 64.6 22.4 68.6 15.2 69.2C19.6 65.2 26.8 56.4 34.8 44.2C40.6 35.4 47.2 29.2 55.6 25.6C57 24.8 58.4 24.4 60.2 24.4Z'

/** `auto` follows the theme: color on light, white on dark. */
export type LogoTone = 'auto' | 'color' | 'mono'

/** Decorative mark. Pair it with the visible product name. */
export function LogoMark({ size = 32, tone = 'auto' }: { size?: number; tone?: LogoTone }) {
  const gradientId = `iatros-${useId().replace(/:/g, '')}`

  return (
    <svg
      className={cx(styles.mark, tone === 'color' && styles.toneColor, tone === 'mono' && styles.toneMono)}
      width={size}
      height={size}
      viewBox="0 0 84 80"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={gradientId} x1="4" y1="16" x2="78" y2="52" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="var(--logo-from)" />
          <stop offset="48%" stopColor="var(--logo-mid)" />
          <stop offset="100%" stopColor="var(--logo-to)" />
        </linearGradient>
      </defs>
      <g className={styles.color} fill={`url(#${gradientId})`}>
        <path d={MARK_A} />
        <path d={MARK_B} />
      </g>
      <g className={styles.mono} fill="currentColor">
        <path d={MARK_A} />
        <path d={MARK_B} />
      </g>
    </svg>
  )
}

/** Color mark on the light rounded tile used as the app icon. */
export function LogoAppIcon({ size = 32 }: { size?: number }) {
  return (
    <span className={styles.appIcon} style={{ width: size, height: size }} aria-hidden="true">
      <LogoMark size={Math.round(size * 0.68)} tone="color" />
    </span>
  )
}

/** Horizontal lockup: mark, wordmark, and optionally the brand tagline. */
export function LogoLockup({
  size = 32,
  tone = 'auto',
  tagline = false,
}: {
  size?: number
  tone?: LogoTone
  tagline?: boolean
}) {
  const { t } = useTranslation()

  return (
    <span className={styles.lockup}>
      <LogoMark size={size} tone={tone} />
      <span className={styles.words}>
        <span className={styles.wordmark}>{t('app.name')}</span>
        {tagline ? <span className={styles.tagline}>{t('app.brandTagline')}</span> : null}
      </span>
    </span>
  )
}
