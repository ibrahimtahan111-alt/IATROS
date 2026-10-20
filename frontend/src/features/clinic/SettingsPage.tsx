import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useUnsavedChangesGuard } from '../../shared/hooks/useUnsavedChangesGuard'
import { ButtonLink } from '../../shared/ui/ButtonLink'
import { ContextBar } from '../../shared/ui/ContextBar'
import { ErrorState } from '../../shared/ui/ErrorState'
import { Page, Section } from '../../shared/ui/Page'
import { LoadingRegion, Skeleton } from '../../shared/ui/Skeleton'
import { UnsavedChangesDialog } from '../../shared/ui/UnsavedChangesDialog'
import { useClinic } from './api'
import { ClinicProfileForm } from './ClinicProfileForm'
import { ClinicSettingsForm } from './ClinicSettingsForm'
import styles from './SettingsPage.module.css'

type FormName = 'profile' | 'settings'

/** Owner-only clinic settings (ui-ux.md §45, requirements.md §5). */
export function SettingsPage() {
  const { t } = useTranslation('clinic')
  const { t: tCommon } = useTranslation()
  const clinic = useClinic()
  // A router supports a single navigation blocker, so the page guards both forms together.
  const [dirtyForms, setDirtyForms] = useState<ReadonlySet<FormName>>(new Set())
  const blocker = useUnsavedChangesGuard(dirtyForms.size > 0)
  const trackDirty = useCallback(
    (form: FormName, dirty: boolean) =>
      setDirtyForms((current) => {
        if (current.has(form) === dirty) return current
        const next = new Set(current)
        if (dirty) next.add(form)
        else next.delete(form)
        return next
      }),
    [],
  )
  const onProfileDirty = useCallback((dirty: boolean) => trackDirty('profile', dirty), [trackDirty])
  const onSettingsDirty = useCallback((dirty: boolean) => trackDirty('settings', dirty), [trackDirty])

  return (
    <Page>
      <ContextBar title={t('title')} />
      {clinic.isPending ? (
        <LoadingRegion label={tCommon('states.loading')}>
          <div className={styles.skeleton}>
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} height="var(--space-10)" />
            ))}
          </div>
        </LoadingRegion>
      ) : clinic.isError ? (
        <ErrorState error={clinic.error} onRetry={() => void clinic.refetch()} retrying={clinic.isFetching} />
      ) : (
        <>
          <Section title={t('profile.title')} description={t('profile.description')}>
            <ClinicProfileForm clinic={clinic.data} onDirtyChange={onProfileDirty} />
          </Section>
          <Section title={t('settings.title')} description={t('settings.description')}>
            <ClinicSettingsForm clinic={clinic.data} onDirtyChange={onSettingsDirty} />
          </Section>
          <Section title={t('account.title')} description={t('account.description')}>
            <div>
              <ButtonLink to="/account">{t('account.link')}</ButtonLink>
            </div>
          </Section>
        </>
      )}
      <UnsavedChangesDialog blocker={blocker} />
    </Page>
  )
}
