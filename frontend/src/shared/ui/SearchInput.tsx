import { Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import inputStyles from './Input.module.css'
import { IconButton } from './IconButton'

interface SearchInputProps {
  /** The committed search term (usually from the URL). */
  value: string
  onChange: (value: string) => void
  /** Accessible name and placeholder, e.g. "Search patients". */
  label: string
  debounceMs?: number
}

/** Search box that commits after typing pauses, so each keystroke does not trigger a server query. */
export function SearchInput({ value, onChange, label, debounceMs = 300 }: SearchInputProps) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState(value)
  const [committed, setCommitted] = useState(value)

  if (value !== committed) {
    setCommitted(value)
    setDraft(value)
  }

  useEffect(() => {
    if (draft === committed) {
      return
    }
    const timer = window.setTimeout(() => onChange(draft.trim()), debounceMs)
    return () => window.clearTimeout(timer)
  }, [draft, committed, debounceMs, onChange])

  return (
    <div className={`${inputStyles.withLeading} ${inputStyles.withTrailing}`} role="search">
      <span className={inputStyles.leading}>
        <Search aria-hidden="true" />
      </span>
      <input
        type="search"
        className={inputStyles.input}
        aria-label={label}
        placeholder={label}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            onChange(draft.trim())
          }
        }}
      />
      {draft && (
        <IconButton
          className={inputStyles.trailing}
          label={t('actions.clearSearch')}
          icon={<X />}
          onClick={() => {
            setDraft('')
            onChange('')
          }}
        />
      )}
    </div>
  )
}
