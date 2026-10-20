import { Construction } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useSessionUser } from '../../features/auth/session'
import { ContextBar } from '../../shared/ui/ContextBar'
import { EmptyState } from '../../shared/ui/EmptyState'
import { NAV_ITEMS, navLabelKey } from '../navigation'

/** Workspace page for a module whose screens arrive in a later roadmap phase. */
export function ModulePage({ path }: { path: string }) {
  const { t } = useTranslation()
  const user = useSessionUser()
  const item = NAV_ITEMS.find((candidate) => candidate.path === path)
  const title = item ? t(navLabelKey(item, user.role) as 'nav.overview') : t('app.name')

  return (
    <>
      <ContextBar title={title} />
      <EmptyState icon={<Construction />} title={t('modulePending.title')} description={t('modulePending.description')} />
    </>
  )
}
