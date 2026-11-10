import { SearchX, UserPlus, UsersRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation } from 'react-router'
import type { SortState } from '../../shared/api/types'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useListSearchParams } from '../../shared/hooks/useListSearchParams'
import { todayIn } from '../../shared/lib/localDates'
import { Button } from '../../shared/ui/Button'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { FilterChips, type ActiveFilter } from '../../shared/ui/FilterChips'
import { Page } from '../../shared/ui/Page'
import { Pagination } from '../../shared/ui/Pagination'
import { SearchInput } from '../../shared/ui/SearchInput'
import { Select } from '../../shared/ui/Select'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { SortableTh, Table, Td, Th } from '../../shared/ui/Table'
import { useSessionUser } from '../auth/session'
import { doctorName, useDoctorChoices } from '../doctors/api'
import { ageOn, patientName, usePatientList, type PatientFilter } from './api'
import { PatientStatusBadge } from './PatientStatusBadge'
import styles from './PatientsPage.module.css'

const FILTERS: readonly PatientFilter[] = ['primaryDoctorId', 'status']
const DEFAULT_SORT: SortState = { field: 'lastName', direction: 'asc' }
const ALL = 'ALL'

/**
 * Patient directory (ui-ux.md §12). The server returns only the patients the caller may see (D-029): doctors their
 * own and authorized patients, so the doctor filter is not offered to them. Archived patients are listed on request.
 * The add-patient dialog renders in the outlet, over the list, keeping its search and filters.
 */
export function PatientsPage() {
  const { t } = useTranslation('patients')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const today = todayIn(user.clinic.timeZone)
  const { search } = useLocation()
  const { params, update, reset } = useListSearchParams(FILTERS, DEFAULT_SORT)
  const list = usePatientList(params)
  const showDoctorFilter = user.role !== 'DOCTOR'
  const doctors = useDoctorChoices(showDoctorFilter)

  const doctorFilter = params.filters.primaryDoctorId
  const filteredDoctor = doctors.data?.find((doctor) => doctor.id === doctorFilter)
  const activeFilters: ActiveFilter[] = [
    ...(doctorFilter
      ? [{ key: 'primaryDoctorId', label: t('filters.primaryDoctor'), value: filteredDoctor ? doctorName(filteredDoctor) : '…' }]
      : []),
    ...(params.filters.status === 'ARCHIVED'
      ? [{ key: 'status', label: t('filters.status'), value: t('status.ARCHIVED') }]
      : []),
  ]
  const narrowed = params.search !== '' || activeFilters.length > 0
  const data = list.data
  const addButton = (
    <ButtonLink to={{ pathname: 'new', search }} variant="primary" icon={<UserPlus aria-hidden="true" />}>
      {t('add')}
    </ButtonLink>
  )

  return (
    <Page width="full">
      <ContextBar title={tCommon('nav.patients')} action={addButton} />

      <div className={styles.toolbar}>
        <div className={styles.search}>
          <SearchInput value={params.search} onChange={(search) => update({ search })} label={t('search')} />
        </div>
        {showDoctorFilter && (
          <div className={styles.filter}>
            <Select
              aria-label={t('filters.primaryDoctor')}
              value={doctorFilter ?? ALL}
              onValueChange={(value) => update({ filters: { primaryDoctorId: value === ALL ? null : value } })}
              options={[
                { value: ALL, label: t('filters.allDoctors') },
                ...(doctors.data ?? []).map((doctor) => ({ value: doctor.id, label: doctorName(doctor) })),
              ]}
            />
          </div>
        )}
        <div className={styles.filter}>
          <Select
            aria-label={t('filters.status')}
            value={params.filters.status === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE'}
            onValueChange={(value) => update({ filters: { status: value === 'ARCHIVED' ? value : null } })}
            options={[
              { value: 'ACTIVE', label: t('status.ACTIVE') },
              { value: 'ARCHIVED', label: t('status.ARCHIVED') },
            ]}
          />
        </div>
        {data && (
          <p className={styles.count} aria-live="polite">
            {t('count', { count: data.totalElements })}
          </p>
        )}
      </div>

      <FilterChips
        filters={activeFilters}
        onRemove={(key) => update({ filters: { [key]: null } })}
        onClearAll={() => update({ filters: { primaryDoctorId: null, status: null } })}
      />

      {list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} retrying={list.isFetching} />
      ) : !data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <div className={styles.skeleton}>
            {Array.from({ length: 6 }, (_, index) => (
              <Skeleton key={index} height="var(--space-10)" />
            ))}
          </div>
        </LoadingRegion>
      ) : data.totalElements === 0 ? (
        narrowed ? (
          <EmptyState
            icon={<SearchX />}
            title={t('noResults.title')}
            description={t('noResults.description')}
            action={<Button onClick={reset}>{t('noResults.clear')}</Button>}
          />
        ) : (
          <EmptyState icon={<UsersRound />} title={t('empty.title')} description={t('empty.description')} action={addButton} />
        )
      ) : (
        <>
          <Table caption={t('caption')} aria-busy={list.isFetching || undefined}>
            <thead>
              <tr>
                <SortableTh field="lastName" sort={params.sort} onSortChange={(sort) => update({ sort })}>
                  {t('columns.name')}
                </SortableTh>
                <SortableTh field="dateOfBirth" sort={params.sort} onSortChange={(sort) => update({ sort })}>
                  {t('columns.dateOfBirth')}
                </SortableTh>
                <Th>{t('columns.contact')}</Th>
                <Th>{t('columns.primaryDoctor')}</Th>
                <Th>{t('columns.nextAppointment')}</Th>
                <Th>{t('columns.status')}</Th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((patient) => (
                <tr key={patient.id}>
                  <Td primary>
                    <Link to={patient.id}>{patientName(patient)}</Link>
                  </Td>
                  <Td className="tabular">
                    {format.localDate(patient.dateOfBirth)}{' '}
                    <span className={styles.muted}>{t('age', { count: ageOn(patient.dateOfBirth, today) })}</span>
                  </Td>
                  <Td>
                    <span className={styles.contact}>
                      {patient.phone && <span>{patient.phone}</span>}
                      {patient.email && <span className={styles.muted}>{patient.email}</span>}
                      {!patient.phone && !patient.email && <span className={styles.muted}>—</span>}
                    </span>
                  </Td>
                  <Td>{patient.primaryDoctor ? doctorName(patient.primaryDoctor) : <span className={styles.muted}>—</span>}</Td>
                  <Td className="tabular">
                    {patient.nextAppointmentAt ? format.dateTime(patient.nextAppointmentAt) : <span className={styles.muted}>—</span>}
                  </Td>
                  <Td>
                    <PatientStatusBadge status={patient.status} />
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination
            page={data.page}
            size={data.size}
            totalElements={data.totalElements}
            onPageChange={(page) => update({ page })}
            onSizeChange={(size) => update({ size })}
          />
        </>
      )}
      <Outlet />
    </Page>
  )
}
