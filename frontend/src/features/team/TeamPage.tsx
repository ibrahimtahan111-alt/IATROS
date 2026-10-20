import { SearchX, UserPlus, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { SortState } from '../../shared/api/types'
import { useListSearchParams } from '../../shared/hooks/useListSearchParams'
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
import { useSpecialtyLabel } from '../doctors/specialties'
import { fullName, useStaffList, type StaffFilter } from './api'
import { MemberActionsMenu } from './MemberActionsMenu'
import { StaffStatusBadge } from './StaffStatusBadge'
import styles from './TeamPage.module.css'
import { useMemberActions } from './useMemberActions'

const FILTERS: readonly StaffFilter[] = ['role', 'status']
const DEFAULT_SORT: SortState = { field: 'lastName', direction: 'asc' }
const ALL = 'ALL'
const ROLES = ['OWNER', 'DOCTOR', 'SECRETARY'] as const
const STATUSES = ['ACTIVE', 'INVITED', 'DEACTIVATED'] as const

/** Owner-only staff directory (ui-ux.md §45, requirements.md §6). */
export function TeamPage() {
  const { t } = useTranslation('team')
  const { t: tCommon } = useTranslation()
  const { params, update, reset } = useListSearchParams(FILTERS, DEFAULT_SORT)
  const list = useStaffList(params)
  const actions = useMemberActions()
  const specialtyLabel = useSpecialtyLabel()

  const filterLabel = { role: t('filters.role'), status: t('filters.status') }
  const filterValue = (key: StaffFilter, value: string) =>
    key === 'role' ? tCommon(`roles.${value as (typeof ROLES)[number]}`) : t(`status.${value as (typeof STATUSES)[number]}`)
  const activeFilters: ActiveFilter[] = FILTERS.flatMap((key) => {
    const value = params.filters[key]
    return value ? [{ key, label: filterLabel[key], value: filterValue(key, value) }] : []
  })
  const narrowed = params.search !== '' || activeFilters.length > 0
  const data = list.data

  return (
    <Page width="full">
      <ContextBar
        title={t('title')}
        action={
          <ButtonLink to="new" variant="primary" icon={<UserPlus aria-hidden="true" />}>
            {t('add')}
          </ButtonLink>
        }
      />

      <div className={styles.toolbar}>
        <div className={styles.search}>
          <SearchInput value={params.search} onChange={(search) => update({ search })} label={t('search')} />
        </div>
        <div className={styles.filter}>
          <Select
            aria-label={t('filters.role')}
            value={params.filters.role ?? ALL}
            onValueChange={(value) => update({ filters: { role: value === ALL ? null : value } })}
            options={[
              { value: ALL, label: t('filters.allRoles') },
              ...ROLES.map((role) => ({ value: role, label: tCommon(`roles.${role}`) })),
            ]}
          />
        </div>
        <div className={styles.filter}>
          <Select
            aria-label={t('filters.status')}
            value={params.filters.status ?? ALL}
            onValueChange={(value) => update({ filters: { status: value === ALL ? null : value } })}
            options={[
              { value: ALL, label: t('filters.allStatuses') },
              ...STATUSES.map((status) => ({ value: status, label: t(`status.${status}`) })),
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
        onClearAll={() => update({ filters: { role: null, status: null } })}
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
          <EmptyState
            icon={<Users />}
            title={t('empty.title')}
            description={t('empty.description')}
            action={
              <ButtonLink to="new" variant="primary" icon={<UserPlus aria-hidden="true" />}>
                {t('add')}
              </ButtonLink>
            }
          />
        )
      ) : (
        <>
          <Table caption={t('caption')} aria-busy={list.isFetching || undefined}>
            <thead>
              <tr>
                <SortableTh field="lastName" sort={params.sort} onSortChange={(sort) => update({ sort })}>
                  {t('columns.name')}
                </SortableTh>
                <SortableTh field="role" sort={params.sort} onSortChange={(sort) => update({ sort })}>
                  {t('columns.role')}
                </SortableTh>
                <Th>{t('columns.specialty')}</Th>
                <SortableTh field="status" sort={params.sort} onSortChange={(sort) => update({ sort })}>
                  {t('columns.status')}
                </SortableTh>
                <Th>{t('columns.contact')}</Th>
                <Th>
                  <span className="visually-hidden">{t('columns.actions')}</span>
                </Th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((member) => (
                <tr key={member.id}>
                  <Td primary>
                    <Link to={member.id}>{fullName(member)}</Link>
                  </Td>
                  <Td>{tCommon(`roles.${member.role}`)}</Td>
                  <Td>{member.doctor ? specialtyLabel(member.doctor.primarySpecialty) : <span className={styles.muted}>—</span>}</Td>
                  <Td>
                    <StaffStatusBadge status={member.status} />
                  </Td>
                  <Td>
                    <span className={styles.contact}>
                      <span>{member.email}</span>
                      {member.phone && <span className={styles.muted}>{member.phone}</span>}
                    </span>
                  </Td>
                  <Td className={styles.actionsCell}>
                    <MemberActionsMenu member={member} actions={actions} />
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

      {actions.dialog}
    </Page>
  )
}
