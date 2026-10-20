import { z } from 'zod/mini'
import { emailField, emptyToNull, optionalText, requiredText } from '../../shared/forms/fields'
import type { CreateStaffRequest, StaffRole, UpdateStaffRequest } from './api'

const contact = {
  firstName: requiredText(100),
  lastName: requiredText(100),
  email: emailField,
  phone: optionalText(40),
}

export const editMemberSchema = z.object(contact)

export const newMemberSchema = z
  .object({
    ...contact,
    role: z.string().check(z.refine((value) => value === 'DOCTOR' || value === 'SECRETARY', { error: 'REQUIRED' })),
    doctor: z.object({
      primarySpecialty: z.string(),
      additionalSpecialty: optionalText(200),
      registrationNumber: optionalText(50),
    }),
  })
  .check(
    z.refine((values) => values.role !== 'DOCTOR' || values.doctor.primarySpecialty !== '', {
      path: ['doctor', 'primarySpecialty'],
      error: 'REQUIRED',
    }),
  )

export type EditMemberValues = z.infer<typeof editMemberSchema>
export type NewMemberValues = z.infer<typeof newMemberSchema>

export const EMPTY_NEW_MEMBER: NewMemberValues = {
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  role: '',
  doctor: { primarySpecialty: '', additionalSpecialty: '', registrationNumber: '' },
}

export function toCreateRequest(values: NewMemberValues): CreateStaffRequest {
  const role = values.role as StaffRole
  return {
    firstName: values.firstName,
    lastName: values.lastName,
    email: values.email,
    phone: emptyToNull(values.phone),
    role,
    doctor:
      role === 'DOCTOR'
        ? {
            primarySpecialty: values.doctor.primarySpecialty,
            additionalSpecialty: emptyToNull(values.doctor.additionalSpecialty),
            registrationNumber: emptyToNull(values.doctor.registrationNumber),
          }
        : null,
  }
}

export function toUpdateRequest(values: EditMemberValues, version: number): UpdateStaffRequest {
  return { ...values, phone: emptyToNull(values.phone), version }
}
