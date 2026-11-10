import { phoneRegionOf, phoneRegionOfLocale } from '../../shared/lib/phoneNumbers'
import { useClinic } from './api'

/** The country preselected in empty phone fields: the clinic's own number, else the browser's language region. */
export function useDefaultPhoneRegion(): string | undefined {
  const clinic = useClinic()
  return phoneRegionOf(clinic.data?.phone) ?? phoneRegionOfLocale(navigator.language)
}
