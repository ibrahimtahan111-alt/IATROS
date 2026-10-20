import { CalendarPlus, ChevronLeft, ChevronRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { useDateTimeFormat } from '../../shared/hooks/useDateTimeFormat'
import { Button } from '../../shared/ui/Button'
import { ContextBar } from '../../shared/ui/ContextBar'
import { EmptyState } from '../../shared/ui/EmptyState'
import { ErrorState } from '../../shared/ui/ErrorState'
import { IconButton } from '../../shared/ui/IconButton'
import { Page } from '../../shared/ui/Page'
import { SegmentedControl } from '../../shared/ui/SegmentedControl'
import { Select } from '../../shared/ui/Select'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { useSessionUser } from '../auth/session'
import { useClinic, type ClinicSettings } from '../clinic/api'
import { doctorName, useDoctorChoices } from '../doctors/api'
import { personName, useCalendar, type CalendarData } from './api'
import { AppointmentPanel } from './AppointmentPanel'
import { BookAppointmentDialog, type BookingDraft } from './BookAppointmentDialog'
import {
  CALENDAR_VIEWS,
  clockOf,
  datesBetween,
  localPoint,
  mergeRanges,
  shiftDate,
  viewRange,
  type CalendarView,
} from './calendarDates'
import { MonthGrid } from './MonthGrid'
import { TimeGrid, type GridColumn } from './TimeGrid'
import styles from './Calendar.module.css'

const ALL = 'ALL'
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/**
 * Calendar-first appointment workspace (ui-ux.md §9–10). View, date, doctor, the open appointment, and whether
 * cancelled appointments show are kept in the URL. The server returns only what the caller may see: doctors their
 * own calendar (D-038).
 */
export function CalendarPage() {
  const { t } = useTranslation()
  const clinic = useClinic()

  if (clinic.isError) {
    return (
      <Page width="full">
        <ErrorState error={clinic.error} onRetry={() => void clinic.refetch()} retrying={clinic.isFetching} />
      </Page>
    )
  }
  if (!clinic.data) {
    return (
      <Page width="full">
        <LoadingRegion label={t('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      </Page>
    )
  }
  return <CalendarWorkspace settings={clinic.data.settings} />
}

function CalendarWorkspace({ settings }: { settings: ClinicSettings }) {
  const { t } = useTranslation('calendar')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const timeZone = user.clinic.timeZone ?? settings.timeZone
  const format = useDateTimeFormat(timeZone)
  const now = useNow(timeZone)
  const [searchParams, setSearchParams] = useSearchParams()
  const [booking, setBooking] = useState<BookingDraft | null>(null)

  const viewParam = searchParams.get('view')
  const view: CalendarView = CALENDAR_VIEWS.includes(viewParam as CalendarView) ? (viewParam as CalendarView) : 'week'
  const dateParam = searchParams.get('date') ?? ''
  const date = DATE_PATTERN.test(dateParam) ? dateParam : now.date
  const doctorFilter = user.role === 'DOCTOR' ? null : searchParams.get('doctor')
  const showCancelled = searchParams.get('cancelled') === '1'
  const appointmentId = searchParams.get('appointment')
  const { from, to } = viewRange(view, date, settings.weekStartDay)

  const calendar = useCalendar(from, to, doctorFilter)
  const doctors = useDoctorChoices(user.role !== 'DOCTOR')

  const update = (changes: Record<string, string | null>) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        return next
      },
      { replace: true },
    )

  const period =
    view === 'day' ? format.longDate(date) : view === 'week' ? format.localDateRange(from, to) : format.monthYear(date)
  const bookingDoctor = (data: CalendarData | undefined) =>
    doctorFilter ?? (data?.doctors.length === 1 ? data.doctors[0]!.id : undefined)
  const bookButton = (
    <Button
      variant="primary"
      icon={<CalendarPlus aria-hidden="true" />}
      onClick={() => setBooking({ date: date < now.date ? now.date : date, doctorId: bookingDoctor(calendar.data) })}
    >
      {t('book.action')}
    </Button>
  )

  return (
    <Page width="full">
      <ContextBar
        title={tCommon('nav.calendar')}
        scope={
          <div className={styles.scope}>
            <div className={styles.navigation}>
              <Button size="sm" onClick={() => update({ date: null })}>
                {t('today')}
              </Button>
              <IconButton label={t('previous')} icon={<ChevronLeft />} onClick={() => update({ date: shiftDate(view, date, -1) })} />
              <IconButton label={t('next')} icon={<ChevronRight />} onClick={() => update({ date: shiftDate(view, date, 1) })} />
            </div>
            <span className={styles.period} aria-live="polite">
              {period}
            </span>
          </div>
        }
        action={bookButton}
      />

      <div className={styles.toolbar}>
        <SegmentedControl
          label={t('view.label')}
          value={view}
          onValueChange={(next) => update({ view: next === 'week' ? null : next })}
          options={CALENDAR_VIEWS.map((value) => ({ value, label: t(`view.${value}`) }))}
        />
        <div className={styles.scope}>
          {user.role !== 'DOCTOR' && (
            <div className={styles.doctorFilter}>
              <Select
                aria-label={t('filters.doctor')}
                value={doctorFilter ?? ALL}
                onValueChange={(value) => update({ doctor: value === ALL ? null : value })}
                options={[
                  { value: ALL, label: t('filters.allDoctors') },
                  ...(doctors.data ?? []).map((doctor) => ({ value: doctor.id, label: doctorName(doctor) })),
                ]}
              />
            </div>
          )}
          <label className={styles.toggle}>
            <input
              type="checkbox"
              checked={showCancelled}
              onChange={(event) => update({ cancelled: event.target.checked ? '1' : null })}
            />
            {t('filters.showCancelled')}
          </label>
        </div>
      </div>

      {calendar.isError ? (
        <ErrorState error={calendar.error} onRetry={() => void calendar.refetch()} retrying={calendar.isFetching} />
      ) : !calendar.data ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <Skeleton height="var(--space-16)" />
        </LoadingRegion>
      ) : (
        <CalendarBody
          data={calendar.data}
          view={view}
          date={date}
          today={now.date}
          now={now}
          showCancelled={showCancelled}
          busy={calendar.isFetching}
          onDayClick={(day) => update({ view: 'day', date: day })}
          onEventClick={(id) => update({ appointment: id })}
          onSlotClick={(column, minutes) =>
            setBooking({
              date: column.date,
              time: clockOf(minutes),
              doctorId: column.doctorId ?? bookingDoctor(calendar.data),
            })
          }
        />
      )}

      <AppointmentPanel appointmentId={appointmentId} onClose={() => update({ appointment: null })} />
      <BookAppointmentDialog
        draft={booking}
        onOpenChange={(open) => !open && setBooking(null)}
        defaultDuration={settings.defaultAppointmentMinutes}
        onBooked={(appointment) => update({ appointment: appointment.id })}
      />
    </Page>
  )
}

interface CalendarBodyProps {
  data: CalendarData
  view: CalendarView
  date: string
  today: string
  now: { date: string; minutes: number }
  showCancelled: boolean
  busy: boolean
  onDayClick: (date: string) => void
  onEventClick: (id: string) => void
  onSlotClick: (column: GridColumn, minutes: number) => void
}

function CalendarBody({ data, view, date, today, now, showCancelled, busy, onDayClick, onEventClick, onSlotClick }: CalendarBodyProps) {
  const { t } = useTranslation('calendar')
  const format = useDateTimeFormat(data.timeZone)
  const events = showCancelled ? data.events : data.events.filter((event) => event.status !== 'CANCELLED')

  if (data.doctors.length === 0) {
    return <EmptyState icon={<CalendarPlus />} title={t('empty.noDoctors.title')} description={t('empty.noDoctors.description')} />
  }

  const hoursOn = (doctorId: string | null, day: string) =>
    mergeRanges(
      data.doctors
        .filter((doctor) => doctorId === null || doctor.id === doctorId)
        .flatMap((doctor) => doctor.days.find((entry) => entry.date === day)?.ranges ?? []),
    )
  const columns: GridColumn[] =
    view === 'day'
      ? data.doctors.map((doctor) => ({
          key: doctor.id,
          date,
          label: personName(doctor),
          doctorId: doctor.id,
          working: hoursOn(doctor.id, date),
        }))
      : datesBetween(data.from, data.to).map((day) => ({
          key: day,
          date: day,
          label: format.weekdayDate(day),
          doctorId: null,
          working: hoursOn(null, day),
        }))
  const doctorNames =
    view === 'week' && data.doctors.length > 1 ? new Map(data.doctors.map((doctor) => [doctor.id, personName(doctor)])) : null

  return (
    <div aria-busy={busy || undefined}>
      {events.length === 0 && <p className={styles.muted}>{t('empty.period')}</p>}
      {view === 'month' ? (
        <MonthGrid
          from={data.from}
          to={data.to}
          month={date}
          events={events}
          timeZone={data.timeZone}
          today={today}
          onDayClick={onDayClick}
          onEventClick={onEventClick}
        />
      ) : (
        <TimeGrid
          columns={columns}
          events={events}
          timeZone={data.timeZone}
          now={now}
          doctorNames={doctorNames}
          onSlotClick={onSlotClick}
          onEventClick={onEventClick}
        />
      )}
    </div>
  )
}

/** Clinic-local date and minute of day, refreshed every minute for the Now line. */
function useNow(timeZone: string): { date: string; minutes: number } {
  const [now, setNow] = useState(() => localPoint(new Date().toISOString(), timeZone))
  useEffect(() => {
    const timer = window.setInterval(() => setNow(localPoint(new Date().toISOString(), timeZone)), 60_000)
    return () => window.clearInterval(timer)
  }, [timeZone])
  return now
}
