import { useMemo, useState, type ChangeEvent, type Ref } from 'react'
import { useTranslation } from 'react-i18next'
import { callingCodeOf, formatPhone, parsePhone, PHONE_REGIONS } from '../lib/phoneNumbers'
import type { FieldControlProps } from './Field'
import { TextInput } from './Input'
import styles from './PhoneInput.module.css'
import { Select, type SelectOption } from './Select'

interface PhoneInputProps extends FieldControlProps {
  /** Read when the input mounts; afterwards the input reports every change through `onChange`. */
  value: string
  onChange: (value: string) => void
  onBlur?: () => void
  name?: string
  ref?: Ref<HTMLInputElement>
  /** Country preselected for an empty value. */
  defaultRegion?: string
}

/** Digits and common separators; a `+` only as the first character. */
const NOT_DIALABLE = /(?!^\+)[^\d\s().-]/g

/**
 * Country calling code and number, reported as one value: `+<code> <number>`. The field label belongs to the number.
 * Typing or pasting a number that starts with a known `+<code>` moves that code into the country select; calling
 * codes are prefix-free, so the first match while typing is the right one.
 */
export function PhoneInput({ value, onChange, onBlur, name, ref, defaultRegion, ...control }: PhoneInputProps) {
  const { t, i18n } = useTranslation()
  const options = useCountryOptions(i18n.language)
  const [initial] = useState(() => parsePhone(value))
  const [chosenRegion, setChosenRegion] = useState(initial.region)
  const [number, setNumber] = useState(initial.number)
  // A stored number without a calling code is shown as is rather than attributed to the default country.
  const region = chosenRegion ?? (initial.number === '' ? defaultRegion : undefined)
  const code = region ? callingCodeOf(region) : undefined

  const report = (nextRegion: string | undefined, nextNumber: string) =>
    onChange(nextNumber.startsWith('+') ? nextNumber : formatPhone(nextRegion, nextNumber))

  const changeNumber = (event: ChangeEvent<HTMLInputElement>) => {
    const typed = event.target.value.replace(NOT_DIALABLE, '').trimStart()
    const international = typed.startsWith('+') ? parsePhone(typed) : undefined
    if (international?.region) {
      setChosenRegion(international.region)
      setNumber(international.number)
      report(international.region, international.number)
      return
    }
    setChosenRegion(region)
    setNumber(typed)
    report(region, typed)
  }

  const changeRegion = (next: string) => {
    setChosenRegion(next)
    report(next, number)
  }

  return (
    <div className={styles.phone}>
      <div className={styles.country}>
        <Select
          aria-label={t('phoneInput.countryCode')}
          value={region}
          onValueChange={changeRegion}
          options={options}
          placeholder={t('phoneInput.countryPlaceholder')}
          valueText={region && code ? `${region} +${code}` : undefined}
        />
      </div>
      <TextInput
        {...control}
        ref={ref}
        name={name}
        type="tel"
        inputMode="tel"
        autoComplete="off"
        className={styles.number}
        value={number}
        onChange={changeNumber}
        onBlur={onBlur}
      />
    </div>
  )
}

function useCountryOptions(language: string): SelectOption[] {
  return useMemo(() => {
    const names = new Intl.DisplayNames([language], { type: 'region' })
    const collator = new Intl.Collator(language)
    return PHONE_REGIONS.map((region) => ({ region, name: names.of(region) ?? region }))
      .sort((a, b) => collator.compare(a.name, b.name))
      .map(({ region, name }) => ({ value: region, label: `${name} (+${callingCodeOf(region)})` }))
  }, [language])
}
