import { CalendarPlus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Badge } from '../../shared/ui/Badge'
import { Button } from '../../shared/ui/Button'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog'
import { ErrorState } from '../../shared/ui/ErrorState'
import { IconButton } from '../../shared/ui/IconButton'
import { Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { Table, Td, Th } from '../../shared/ui/Table'
import { useToast } from '../../shared/ui/useToast'
import { AddExceptionDialog } from './AddExceptionDialog'
import { useDeleteScheduleException, useScheduleExceptions, type ScheduleException } from './api'
import styles from './SchedulesPage.module.css'

interface ExceptionsSectionProps {
  doctorId: string
  timeZone: string
  today: string
}

/** Upcoming one-off changes: days off, partial absences, and extra hours (decisions.md D-026). */
export function ExceptionsSection({ doctorId, timeZone, today }: ExceptionsSectionProps) {
  const { t } = useTranslation('schedules')
  const { t: tCommon } = useTranslation()
  const format = useDateTimeFormat(timeZone)
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const exceptions = useScheduleExceptions(doctorId)
  const remove = useDeleteScheduleException(doctorId)
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<ScheduleException | null>(null)

  const hours = (exception: ScheduleException) =>
    exception.startTime && exception.endTime ? format.timeRange(exception.startTime, exception.endTime) : t('exceptions.allDay')

  const confirmRemove = () => {
    if (!removing) return
    remove.mutate(removing.id, {
      onSuccess: () => toast(t('exceptions.removed')),
      onError: (error) => toast(errorMessage(error), 'danger'),
      onSettled: () => setRemoving(null),
    })
  }

  return (
    <Section
      title={t('exceptions.title')}
      description={t('exceptions.description')}
      action={
        <Button size="sm" icon={<CalendarPlus aria-hidden="true" />} onClick={() => setAdding(true)}>
          {t('exceptions.add')}
        </Button>
      }
    >
      {exceptions.isPending && (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-12)" />
        </LoadingRegion>
      )}
      {exceptions.isError && (
        <ErrorState error={exceptions.error} onRetry={() => void exceptions.refetch()} retrying={exceptions.isFetching} />
      )}
      {exceptions.data?.length === 0 && <p className={styles.note}>{t('exceptions.empty')}</p>}
      {exceptions.data && exceptions.data.length > 0 && (
        <Table caption={t('exceptions.caption')}>
          <thead>
            <tr>
              <Th>{t('exceptions.fields.date')}</Th>
              <Th>{t('exceptions.fields.type')}</Th>
              <Th>{t('exceptions.fields.hours')}</Th>
              <Th>{t('exceptions.fields.reason')}</Th>
              <Th>
                <span className="visually-hidden">{t('exceptions.actions')}</span>
              </Th>
            </tr>
          </thead>
          <tbody>
            {exceptions.data.map((exception) => (
              <tr key={exception.id}>
                <Td primary className="tabular">
                  {format.weekdayDate(exception.date)}
                </Td>
                <Td>
                  <Badge tone={exception.type === 'UNAVAILABLE' ? 'warning' : 'success'}>
                    {t(`exceptions.type.${exception.type}`)}
                  </Badge>
                </Td>
                <Td className="tabular">{hours(exception)}</Td>
                <Td>{exception.reason ?? ''}</Td>
                <Td className={styles.actionsCell}>
                  <IconButton
                    label={t('exceptions.remove', { date: format.localDate(exception.date) })}
                    icon={<Trash2 />}
                    onClick={() => setRemoving(exception)}
                  />
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <AddExceptionDialog doctorId={doctorId} today={today} open={adding} onOpenChange={setAdding} />
      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={t('exceptions.removeTitle')}
        consequence={removing ? t('exceptions.removeConsequence', { date: format.localDate(removing.date) }) : ''}
        confirmLabel={t('exceptions.removeConfirm')}
        onConfirm={confirmRemove}
        destructive
        pending={remove.isPending}
      />
    </Section>
  )
}
