import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { ModuleSwitch } from '../../app/ModuleSwitch'
import { navLabelKey, NAV_ITEMS } from '../../app/navigation'
import { ContextBar } from '../../shared/ui/ContextBar'
import { Page } from '../../shared/ui/Page'
import { Select } from '../../shared/ui/Select'
import { useSessionUser } from '../auth/session'
import { doctorName, useDoctorChoices } from '../doctors/api'
import { ActivityReport } from './ActivityReport'
import { type KpiPeriod } from './api'
import { PeriodControl } from './PeriodControl'
import { DEFAULT_PERIOD, isKpiPeriod } from './period'
import styles from './Activity.module.css'

const ALL = 'ALL'

/** KPIs and clinic activity (ui-ux.md §22). Owners see the clinic or one doctor; doctors only themselves. */
export function ActivityPage() {
  const { t } = useTranslation('activity')
  const { t: tCommon } = useTranslation()
  const user = useSessionUser()
  const [searchParams, setSearchParams] = useSearchParams()
  const isOwner = user.role === 'OWNER'

  const periodParam = searchParams.get('period')
  const period: KpiPeriod = isKpiPeriod(periodParam) ? periodParam : DEFAULT_PERIOD
  const doctorFilter = isOwner ? searchParams.get('doctor') : null
  const doctors = useDoctorChoices(isOwner)

  const update = (changes: Record<string, string | null>) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        if (!('page' in changes)) next.delete('page')
        return next
      },
      { replace: true },
    )

  const navItem = NAV_ITEMS.find((item) => item.path === '/activity')!
  const title = tCommon(navLabelKey(navItem, user.role) as 'nav.activity')

  return (
    <Page width="full">
      <ContextBar title={title} scope={<ModuleSwitch current="activity" />} />
      <div className={styles.toolbar}>
        <PeriodControl
          value={period}
          onValueChange={(next) => update({ period: next === DEFAULT_PERIOD ? null : next })}
        />
        {isOwner && (
          <Select
            variant="filter"
            aria-label={t('filters.doctor')}
            value={doctorFilter ?? ALL}
            onValueChange={(value) => update({ doctor: value === ALL ? null : value })}
            options={[
              { value: ALL, label: t('filters.allDoctors') },
              ...(doctors.data ?? []).map((doctor) => ({
                value: doctor.id,
                label: doctorName(doctor),
              })),
            ]}
          />
        )}
      </div>
      <ActivityReport
        period={period}
        doctorId={isOwner ? doctorFilter : user.doctorId}
        clinicWide={isOwner}
        onDoctor={(id) => update({ doctor: id })}
      />
    </Page>
  )
}
