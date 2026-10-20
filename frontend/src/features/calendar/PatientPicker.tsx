import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Button } from '../../shared/ui/Button'
import { SearchInput } from '../../shared/ui/SearchInput'
import { patientName, usePatientList, type PatientFilter } from '../patients/api'
import type { ListParams } from '../../shared/hooks/useListSearchParams'
import { personName, type NameRef } from './api'
import styles from './Calendar.module.css'

const RESULTS = 8

interface PatientPickerProps {
  value: NameRef | null
  onChange: (patient: NameRef | null) => void
  timeZone: string | null
  /** Error text for the field, already translated. */
  error?: string
}

/**
 * Server-side patient search for booking. The list holds only active patients the caller may see (D-029), so the
 * picker never offers a patient the booking would be refused for.
 */
export function PatientPicker({ value, onChange, timeZone, error }: PatientPickerProps) {
  const { t } = useTranslation('calendar')
  const format = useDateTimeFormat(timeZone)
  const [search, setSearch] = useState('')
  const params: ListParams<PatientFilter> = {
    page: 0,
    size: RESULTS,
    sort: { field: 'lastName', direction: 'asc' },
    search,
    filters: {},
  }
  const list = usePatientList(params)

  if (value) {
    return (
      <div className={styles.picked}>
        <span>{personName(value)}</span>
        <Button size="sm" variant="ghost" onClick={() => onChange(null)}>
          {t('book.changePatient')}
        </Button>
      </div>
    )
  }

  const results = list.data?.content ?? []
  return (
    <div>
      <SearchInput value={search} onChange={setSearch} label={t('book.searchPatient')} />
      {error && (
        <p role="alert" className={styles.fieldError}>
          {error}
        </p>
      )}
      {search !== '' && list.data && results.length === 0 && <p className={styles.muted}>{t('book.noPatients')}</p>}
      {search !== '' && results.length > 0 && (
        <ul className={styles.results} aria-label={t('book.patientResults')}>
          {results.map((patient) => (
            <li key={patient.id}>
              <button
                type="button"
                className={styles.result}
                onClick={() => onChange({ id: patient.id, firstName: patient.firstName, lastName: patient.lastName })}
              >
                <span>{patientName(patient)}</span>
                <span className={`${styles.muted} tabular`}>{format.localDate(patient.dateOfBirth)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}