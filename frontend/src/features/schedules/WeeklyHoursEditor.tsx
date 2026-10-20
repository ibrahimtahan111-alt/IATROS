import { standardSchemaResolver } from '@hookform/resolvers/standard-schema'
import { Plus, Trash2 } from 'lucide-react'
import { useEffect, useId } from 'react'
import { useFieldArray, useForm, type FieldError } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { isApiError } from '../../shared/api/ApiError'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { applyFieldErrors, hasFieldErrors } from '../../shared/api/formErrors'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { minutesOf } from '../../shared/lib/localDates'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { IconButton } from '../../shared/ui/IconButton'
import { TextInput } from '../../shared/ui/Input'
import { FormActions } from '../../shared/ui/Page'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useToast } from '../../shared/ui/useToast'
import { useReplaceWeeklySchedule, type WeeklySchedule } from './api'
import { weekSchema, weekValuesOf as valuesOf, type WeekValues } from './schemas'
import styles from './WeeklyHoursEditor.module.css'

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const

/** A new period starts where the day's last one ends (a one-hour slot), or with a morning block on an empty day. */
function nextPeriod(dayOfWeek: number, existing: readonly { startTime: string; endTime: string }[]) {
  const lastEnd = existing.map((period) => period.endTime).filter(Boolean).sort((a, b) => minutesOf(a) - minutesOf(b)).at(-1)
  if (!lastEnd) return { dayOfWeek, startTime: '09:00', endTime: '13:00' }
  const end = Math.min(minutesOf(lastEnd) + 60, 23 * 60 + 59)
  const format = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
  return { dayOfWeek, startTime: lastEnd, endTime: format(end) }
}

/**
 * Weekly working periods, edited as a whole week and saved in one request (decisions.md D-035). The gaps between a
 * day's periods are its breaks.
 */
export function WeeklyHoursEditor({ schedule }: { schedule: WeeklySchedule }) {
  const { t } = useTranslation('schedules')
  const { t: tCommon } = useTranslation()
  const toast = useToast()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const format = useDateTimeFormat(schedule.timeZone)
  const errorIdPrefix = useId()
  const replace = useReplaceWeeklySchedule(schedule.doctorId)
  const form = useForm<WeekValues>({
    resolver: standardSchemaResolver(weekSchema),
    defaultValues: valuesOf(schedule),
  })
  const { control, register, reset, setError, setFocus, handleSubmit, formState, getValues } = form
  const { fields, append, remove } = useFieldArray({ control, name: 'periods' })
  const blocker = useUnsavedChangesGuard(formState.isDirty, { searchParams: ['doctor'] })
  const conflict = isApiError(replace.error) && replace.error.code === 'CONCURRENT_MODIFICATION'

  useEffect(() => {
    if (!formState.isDirty && schedule.version !== getValues('version')) {
      reset(valuesOf(schedule))
    }
  }, [schedule, formState.isDirty, getValues, reset])

  const onSubmit = handleSubmit(({ periods, version }) =>
    replace.mutate(
      { periods, version },
      {
        onSuccess: (saved) => {
          reset(valuesOf(saved))
          toast(t('weekly.saved'))
        },
        onError: (error) => applyFieldErrors(error, setError, setFocus),
      },
    ),
  )

  const discard = () => {
    reset(valuesOf(schedule))
    replace.reset()
  }

  return (
    <form onSubmit={onSubmit} noValidate className={styles.form}>
      {replace.error && !hasFieldErrors(replace.error) && (
        <Alert
          tone="danger"
          action={
            conflict ? (
              <Button size="sm" onClick={discard}>
                {t('weekly.loadLatest')}
              </Button>
            ) : undefined
          }
        >
          {errorMessage(replace.error)}
        </Alert>
      )}

      <div className={styles.week}>
        {DAYS.map((day) => {
          const dayName = format.weekday(day)
          const dayFields = fields.map((field, index) => ({ field, index })).filter(({ field }) => field.dayOfWeek === day)
          return (
            <div key={day} className={styles.day} role="group" aria-label={dayName}>
              <div className={styles.dayName} aria-hidden="true">
                {dayName}
              </div>
              <div className={styles.periods}>
                {dayFields.length === 0 && <p className={styles.off}>{t('weekly.notWorking')}</p>}
                {dayFields.map(({ field, index }, position) => {
                  const errors = formState.errors.periods?.[index]
                  const error = (errors?.startTime ?? errors?.endTime) as FieldError | undefined
                  const errorId = `${errorIdPrefix}-${index}`
                  const describedBy = error ? errorId : undefined
                  return (
                    <div key={field.id} className={styles.periodRow}>
                      <div className={styles.period}>
                        <TextInput
                          type="time"
                          step={60}
                          className={styles.time}
                          aria-label={t('weekly.start', { day: dayName, number: position + 1 })}
                          aria-invalid={errors?.startTime ? true : undefined}
                          aria-describedby={describedBy}
                          {...register(`periods.${index}.startTime`)}
                        />
                        <span aria-hidden="true">–</span>
                        <TextInput
                          type="time"
                          step={60}
                          className={styles.time}
                          aria-label={t('weekly.end', { day: dayName, number: position + 1 })}
                          aria-invalid={errors?.endTime ? true : undefined}
                          aria-describedby={describedBy}
                          {...register(`periods.${index}.endTime`)}
                        />
                        <IconButton
                          label={t('weekly.remove', { day: dayName, number: position + 1 })}
                          icon={<Trash2 />}
                          onClick={() => remove(index)}
                        />
                      </div>
                      {error && (
                        <p id={errorId} className={styles.error}>
                          {fieldError(error.message)}
                        </p>
                      )}
                    </div>
                  )
                })}
                <div>
                  <Button
                    size="sm"
                    variant="ghost"
                    icon={<Plus aria-hidden="true" />}
                    aria-label={t('weekly.addTo', { day: dayName })}
                    onClick={() =>
                      append(nextPeriod(day, getValues('periods').filter((period) => period.dayOfWeek === day)), {
                        shouldFocus: true,
                      })
                    }
                  >
                    {t('weekly.add')}
                  </Button>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <FormActions>
        <Button type="submit" variant="primary" loading={replace.isPending} disabled={!formState.isDirty}>
          {t('weekly.save')}
        </Button>
        {formState.isDirty && (
          <Button variant="ghost" onClick={discard} disabled={replace.isPending}>
            {tCommon('unsavedChanges.discard')}
          </Button>
        )}
      </FormActions>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}
