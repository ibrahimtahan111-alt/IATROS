import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

/** Counts and percentages in the interface language (internationalization.md). */
export function useNumberFormat() {
  const { i18n } = useTranslation()
  const language = i18n.language

  return useMemo(() => {
    const integer = new Intl.NumberFormat(language, {
      maximumFractionDigits: 0,
    })
    const percent = new Intl.NumberFormat(language, {
      style: 'percent',
      maximumFractionDigits: 0,
    })
    const signedPercent = new Intl.NumberFormat(language, {
      style: 'percent',
      maximumFractionDigits: 0,
      signDisplay: 'exceptZero',
    })
    const signedInteger = new Intl.NumberFormat(language, {
      maximumFractionDigits: 0,
      signDisplay: 'exceptZero',
    })
    const unit = (name: string, fractionDigits: number) =>
      new Intl.NumberFormat(language, { style: 'unit', unit: name, maximumFractionDigits: fractionDigits })
    const bytes = unit('byte', 0)
    const kilobytes = unit('kilobyte', 0)
    const megabytes = unit('megabyte', 1)
    return {
      integer: (value: number) => integer.format(value),
      /** A file size in decimal units, e.g. 1 450 000 → "1.5 MB". */
      fileSize: (value: number) =>
        value < 1000 ? bytes.format(value) : value < 1_000_000 ? kilobytes.format(value / 1000) : megabytes.format(value / 1_000_000),
      /** A fraction between 0 and 1, e.g. 0.25 → "25%". */
      percent: (fraction: number) => percent.format(fraction),
      /** A relative change, e.g. 0.12 → "+12%". */
      signedPercent: (fraction: number) => signedPercent.format(fraction),
      signedInteger: (value: number) => signedInteger.format(value),
    }
  }, [language])
}
