import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import styles from './CenteredPage.module.css'

export function NotFoundPage() {
  const { t } = useTranslation()

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <h1 className={styles.title}>{t('notFound.title')}</h1>
        <p className={styles.lead}>{t('notFound.description')}</p>
        <Link className={styles.link} to="/">
          {t('notFound.action')}
        </Link>
      </div>
    </main>
  )
}
