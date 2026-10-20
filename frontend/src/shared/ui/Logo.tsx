import styles from './Logo.module.css'

/** IATROS mark: a pulse line on an Aegean teal tile. Decorative; pair it with the visible product name. */
export function LogoMark({ size = 32 }: { size?: number }) {
  return (
    <svg className={styles.mark} width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="8" className={styles.tile} />
      <path
        d="M9 17h4l2-5 3 9 2-4h3"
        fill="none"
        className={styles.pulse}
        strokeWidth="2.25"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
