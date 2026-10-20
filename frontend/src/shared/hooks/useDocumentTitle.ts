import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

/** "Page · IATROS" in the browser tab, following the active language. */
export function useDocumentTitle(title: string): void {
  const { t } = useTranslation()
  useEffect(() => {
    document.title = `${title} · ${t('app.name')}`
  }, [title, t])
}
