import { z } from 'zod/mini'
import { emptyToNull, optionalEmailField, optionalText, requiredText } from '../../shared/forms/fields'
import type { Clinic, UpdateClinicRequest } from './api'

export const clinicProfileSchema = z.object({
  name: requiredText(200),
  email: optionalEmailField,
  phone: optionalText(40),
  address: optionalText(500),
})

export type ClinicProfileValues = z.infer<typeof clinicProfileSchema>

export function profileValues(clinic: Clinic): ClinicProfileValues {
  return { name: clinic.name, email: clinic.email ?? '', phone: clinic.phone ?? '', address: clinic.address ?? '' }
}

export function toUpdateClinicRequest(values: ClinicProfileValues, version: number): UpdateClinicRequest {
  return {
    name: values.name,
    email: emptyToNull(values.email),
    phone: emptyToNull(values.phone),
    address: emptyToNull(values.address),
    version,
  }
}
