import { useTranslation } from 'react-i18next'
import { Spinner } from '../shared/ui/Spinner'
import styles from './AppLoading.module.css'

/** Initial application load only; pages and sections use skeletons (design-system.md §11.10). */
export function AppLoading() {
  const { t } = useTranslation()
  return (
    <div className={styles.loading} role="status" aria-live="polite">
      <Spinner size="md" />
      <span className="visually-hidden">{t('states.loading')}</span>
    </div>
  )
}
