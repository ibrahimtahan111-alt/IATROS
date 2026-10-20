import { Controller, useWatch, type Path, type PathValue, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useFieldErrorMessage } from '../../shared/api/errorMessage'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { localDateTimeOf, todayIn } from '../../shared/lib/localDates'
import { Field } from '../../shared/ui/Field'
import { DateInput, TextInput } from '../../shared/ui/Input'
import { FormRow } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { useSessionUser } from '../auth/session'
import { useClinic } from '../clinic/api'
import { doctorName, useDoctorChoices } from '../doctors/api'
import { useAvailability } from './api'
import { durationsWith, type RescheduleValues } from './schemas'
import styles from './Calendar.module.css'

interface AppointmentTimeFieldsProps<T extends RescheduleValues> {
  form: UseFormReturn<T>
  /** Doctors book only for themselves (D-038), so their doctor is not offered as a choice. */
  doctorFixed: boolean
}

/** Doctor, date, duration, and start time, with the doctor's free start times as shortcuts. */
export function AppointmentTimeFields<T extends RescheduleValues>({ form, doctorFixed }: AppointmentTimeFieldsProps<T>) {
  const { t } = useTranslation('calendar')
  const user = useSessionUser()
  const clinic = useClinic()
  const fieldError = useFieldErrorMessage()
  const format = useDateTimeFormat(user.clinic.timeZone)
  const doctors = useDoctorChoices(!doctorFixed)
  const { control, register, setValue, formState } = form
  const name = (field: keyof RescheduleValues) => field as Path<T>
  const errorOf = (field: keyof RescheduleValues) =>
    fieldError((formState.errors as Partial<Record<keyof RescheduleValues, { message?: string }>>)[field]?.message)

  const [doctorId, date, duration, time] = useWatch({
    control,
    name: [name('doctorId'), name('date'), name('duration'), name('time')],
  }) as [string, string, string, string]
  const availability = useAvailability(doctorId, date, Number(duration))
  const durations = durationsWith(clinic.data?.settings.defaultAppointmentMinutes ?? 0, Number(duration))
  const activeDoctors = (doctors.data ?? []).filter((doctor) => doctor.status === 'ACTIVE' || doctor.id === doctorId)

  return (
    <>
      {!doctorFixed && (
        <Field label={t('fields.doctor')} error={errorOf('doctorId')}>
          {(controlProps) => (
            <Controller
              control={control}
              name={name('doctorId')}
              render={({ field }) => (
                <Select
                  {...controlProps}
                  name={field.name}
                  value={(field.value as string) || undefined}
                  onValueChange={field.onChange}
                  placeholder={t('book.chooseDoctor')}
                  options={activeDoctors.map((doctor) => ({ value: doctor.id, label: doctorName(doctor) }))}
                />
              )}
            />
          )}
        </Field>
      )}
      <FormRow>
        <Field label={t('fields.date')} error={errorOf('date')}>
          {(controlProps) => <DateInput {...controlProps} min={todayIn(user.clinic.timeZone)} {...register(name('date'))} />}
        </Field>
        <Field label={t('fields.duration')} error={errorOf('duration')}>
          {(controlProps) => (
            <Controller
              control={control}
              name={name('duration')}
              render={({ field }) => (
                <Select
                  {...controlProps}
                  name={field.name}
                  value={field.value as string}
                  onValueChange={field.onChange}
                  options={durations.map((minutes) => ({ value: String(minutes), label: t('minutes', { count: minutes }) }))}
                />
              )}
            />
          )}
        </Field>
        <Field label={t('fields.time')} error={errorOf('time')}>
          {(controlProps) => <TextInput type="time" step={300} {...controlProps} {...register(name('time'))} />}
        </Field>
      </FormRow>
      {doctorId && availability.data && (
        <div>
          <p className={styles.slotsLabel}>{t('book.freeTimes')}</p>
          {availability.data.slots.length === 0 ? (
            <p className={styles.muted}>{t('book.noFreeTimes')}</p>
          ) : (
            <div className={styles.slots} role="group" aria-label={t('book.freeTimes')}>
              {availability.data.slots.map((slot) => {
                const slotTime = localDateTimeOf(slot.startAt, user.clinic.timeZone).slice(11)
                return (
                  <button
                    key={slot.startAt}
                    type="button"
                    className={styles.slot}
                    aria-pressed={slotTime === time}
                    onClick={() =>
                      setValue(name('time'), slotTime as PathValue<T, Path<T>>, { shouldValidate: true, shouldDirty: true })
                    }
                  >
                    {format.timeOf(slot.startAt)}
                  </button>
                )
              })}
            </div>
          )}
        </div>
      )}
    </>
  )
}
