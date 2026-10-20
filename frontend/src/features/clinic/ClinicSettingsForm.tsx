import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { LANGUAGE_OPTIONS, type Language } from '../../i18n/languages'
import { useErrorMessage, useFieldErrorMessage } from '../../shared/api/errorMessage'
import { hasFieldErrors } from '../../shared/api/formErrors'
import { isApiError } from '../../shared/api/ApiError'
import { Alert } from '../../shared/ui/Alert'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { FormActions, FormRow, FormStack } from '../../shared/ui/Page'
import { Select, type SelectOption } from '../../shared/ui/Select'
import { useToast } from '../../shared/ui/useToast'
import { useUpdateClinicSettings, type Clinic, type ClinicSettings } from './api'

const APPOINTMENT_LENGTHS = [10, 15, 20, 30, 45, 60, 90]
/** 2024-01-01 was a Monday, so day N of that week is ISO weekday N. */
const isoWeekday = (day: number) => new Date(Date.UTC(2024, 0, day))

interface ClinicSettingsFormProps {
  clinic: Clinic
  onDirtyChange: (dirty: boolean) => void
}

type Draft = Omit<ClinicSettings, 'version'>

function draftOf(settings: ClinicSettings): Draft {
  const { timeZone, defaultAppointmentMinutes, defaultLanguage, weekStartDay } = settings
  return { timeZone, defaultAppointmentMinutes, defaultLanguage, weekStartDay }
}

/** Every value comes from a fixed list, so there is nothing to validate client-side beyond the backend's checks. */
export function ClinicSettingsForm({ clinic, onDirtyChange }: ClinicSettingsFormProps) {
  const { t, i18n } = useTranslation('clinic')
  const { t: tCommon } = useTranslation()
  const toast = useToast()
  const update = useUpdateClinicSettings()
  const errorMessage = useErrorMessage()
  const fieldError = useFieldErrorMessage()
  const [saved, setSaved] = useState(() => draftOf(clinic.settings))
  const [draft, setDraft] = useState(saved)
  const dirty = (Object.keys(draft) as (keyof Draft)[]).some((key) => draft[key] !== saved[key])
  const serverError = (field: keyof Draft) =>
    isApiError(update.error) ? fieldError(update.error.fieldError(field)) : undefined

  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange])

  const timeZones = useMemo(() => withCurrent(Intl.supportedValuesOf('timeZone'), saved.timeZone), [saved.timeZone])
  const timeZoneOptions: SelectOption[] = timeZones.map((zone) => ({ value: zone, label: zone.replaceAll('_', ' ') }))
  const lengthOptions: SelectOption[] = withCurrent(APPOINTMENT_LENGTHS, saved.defaultAppointmentMinutes)
    .sort((a, b) => a - b)
    .map((minutes) => ({ value: String(minutes), label: t('settings.minutes', { count: minutes }) }))
  const weekdayFormat = new Intl.DateTimeFormat(i18n.language, { weekday: 'long', timeZone: 'UTC' })
  const weekStartOptions: SelectOption[] = [1, 6, 7].map((day) => ({
    value: String(day),
    label: capitalize(weekdayFormat.format(isoWeekday(day)), i18n.language),
  }))

  const change = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft((current) => ({ ...current, [key]: value }))

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    update.mutate(
      { ...draft, version: clinic.settings.version },
      {
        onSuccess: (result) => {
          const next = draftOf(result.settings)
          setSaved(next)
          setDraft(next)
          toast(t('settings.saved'))
        },
      },
    )
  }

  return (
    <FormStack onSubmit={onSubmit}>
      {update.error && !hasFieldErrors(update.error) && <Alert tone="danger">{errorMessage(update.error)}</Alert>}
      <Field label={t('settings.timeZone')} helper={t('settings.timeZoneHelp')} error={serverError('timeZone')}>
        {(control) => (
          <Select
            {...control}
            name="timeZone"
            value={draft.timeZone}
            onValueChange={(value) => change('timeZone', value)}
            options={timeZoneOptions}
          />
        )}
      </Field>
      <FormRow>
        <Field label={t('settings.defaultAppointmentMinutes')} error={serverError('defaultAppointmentMinutes')}>
          {(control) => (
            <Select
              {...control}
              name="defaultAppointmentMinutes"
              value={String(draft.defaultAppointmentMinutes)}
              onValueChange={(value) => change('defaultAppointmentMinutes', Number(value))}
              options={lengthOptions}
            />
          )}
        </Field>
        <Field label={t('settings.weekStartDay')} error={serverError('weekStartDay')}>
          {(control) => (
            <Select
              {...control}
              name="weekStartDay"
              value={String(draft.weekStartDay)}
              onValueChange={(value) => change('weekStartDay', Number(value))}
              options={withCurrentOption(weekStartOptions, draft.weekStartDay, weekdayFormat, i18n.language)}
            />
          )}
        </Field>
      </FormRow>
      <Field
        label={t('settings.defaultLanguage')}
        helper={t('settings.defaultLanguageHelp')}
        error={serverError('defaultLanguage')}
      >
        {(control) => (
          <Select
            {...control}
            name="defaultLanguage"
            value={draft.defaultLanguage}
            onValueChange={(value) => change('defaultLanguage', value as Language)}
            options={LANGUAGE_OPTIONS}
          />
        )}
      </Field>
      <FormActions>
        <Button type="submit" variant="primary" loading={update.isPending} disabled={!dirty}>
          {tCommon('actions.save')}
        </Button>
      </FormActions>
    </FormStack>
  )
}

function withCurrent<T>(values: readonly T[], current: T): T[] {
  return values.includes(current) ? [...values] : [current, ...values]
}

/** Monday, Saturday, and Sunday cover common conventions; any other stored value stays selectable. */
function withCurrentOption(options: SelectOption[], day: number, format: Intl.DateTimeFormat, language: string) {
  if (options.some((option) => option.value === String(day))) return options
  return [...options, { value: String(day), label: capitalize(format.format(isoWeekday(day)), language) }]
}

function capitalize(value: string, language: string): string {
  return value.charAt(0).toLocaleUpperCase(language) + value.slice(1)
}
