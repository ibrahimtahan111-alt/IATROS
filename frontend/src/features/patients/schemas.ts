import { z } from 'zod/mini'
import { emptyToNull, optionalEmailField, optionalText, requiredText } from '../../shared/forms/fields'
import type { CreatePatientRequest, Gender, Patient, PatientDetailsRequest, UpdatePatientRequest } from './api'

const EARLIEST_BIRTH_DATE = '1900-01-01'

/** Mirrors the server rules (decisions.md D-036) so client and server report the same codes on the same fields. */
function detailsShape(today: string) {
  return {
    firstName: requiredText(100),
    lastName: requiredText(100),
    dateOfBirth: z
      .string()
      .check(
        z.minLength(1, { error: 'REQUIRED', abort: true }),
        z.refine((value) => value >= EARLIEST_BIRTH_DATE, { error: 'OUT_OF_RANGE', abort: true }),
        z.refine((value) => value <= today, { error: 'DATE_IN_FUTURE' }),
      ),
    gender: z.string(),
    phone: optionalText(40),
    email: optionalEmailField,
    address: optionalText(500),
    emergencyContactName: optionalText(200),
    emergencyContactPhone: optionalText(40),
  }
}

/** `today` is the clinic-local date: a birth date cannot be later. */
export function patientDetailsSchema(today: string) {
  return z.object(detailsShape(today))
}

export function newPatientSchema(today: string) {
  return z.object({ ...detailsShape(today), primaryDoctorId: z.string().check(z.minLength(1, { error: 'REQUIRED' })) })
}

export type PatientDetailsValues = z.infer<ReturnType<typeof patientDetailsSchema>>
export type NewPatientValues = z.infer<ReturnType<typeof newPatientSchema>>

const EMPTY_DETAILS: PatientDetailsValues = {
  firstName: '',
  lastName: '',
  dateOfBirth: '',
  gender: '',
  phone: '',
  email: '',
  address: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
}

export function emptyNewPatient(primaryDoctorId: string): NewPatientValues {
  return { ...EMPTY_DETAILS, primaryDoctorId }
}

export function patientDetailsOf(patient: Patient): PatientDetailsValues {
  return {
    firstName: patient.firstName,
    lastName: patient.lastName,
    dateOfBirth: patient.dateOfBirth,
    gender: patient.gender ?? '',
    phone: patient.phone ?? '',
    email: patient.email ?? '',
    address: patient.address ?? '',
    emergencyContactName: patient.emergencyContactName ?? '',
    emergencyContactPhone: patient.emergencyContactPhone ?? '',
  }
}

function toDetailsRequest(values: PatientDetailsValues): PatientDetailsRequest {
  return {
    firstName: values.firstName,
    lastName: values.lastName,
    dateOfBirth: values.dateOfBirth,
    gender: values.gender === '' ? null : (values.gender as Gender),
    phone: emptyToNull(values.phone),
    email: emptyToNull(values.email),
    address: emptyToNull(values.address),
    emergencyContactName: emptyToNull(values.emergencyContactName),
    emergencyContactPhone: emptyToNull(values.emergencyContactPhone),
  }
}

export function toCreatePatientRequest(values: NewPatientValues): CreatePatientRequest {
  return { ...toDetailsRequest(values), primaryDoctorId: values.primaryDoctorId }
}

export function toUpdatePatientRequest(values: PatientDetailsValues, version: number): UpdatePatientRequest {
  return { ...toDetailsRequest(values), version }
}
