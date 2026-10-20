import { useTranslation } from 'react-i18next'

/** Greeting by time of day in the clinic's zone. */
export function useGreeting(timeZone: string | null | undefined) {
  const { t } = useTranslation('dashboard')
  const hour = Number(
    new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit',
      hourCycle: 'h23',
      timeZone: timeZone ?? undefined,
    }).format(new Date()),
  )
  const part = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'
  return (name: string) => t(`greeting.${part}`, { name })
}
