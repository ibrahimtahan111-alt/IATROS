import { z } from 'zod/mini'
import { emptyToNull, optionalText, requiredText } from '../../shared/forms/fields'
import { instantOf } from '../../shared/lib/localDates'
import type {
  Allergy,
  AllergyRequest,
  AllergySeverity,
  Condition,
  ConditionRequest,
  ConditionStatus,
  HistoryEntry,
  HistoryEntryRequest,
  Medication,
  MedicationRequest,
} from './api'
import type { Consultation, ConsultationRequest } from './consultationsApi'

// Limits and codes mirror the server DTOs and rules (api.md §12–16, decisions.md D-037).

/** A "YYYY-MM-DD" that may be empty but not later than today (clinic-local). */
function pastDate(today: string) {
  return z.string().check(z.refine((value) => value === '' || value <= today, { error: 'DATE_IN_FUTURE' }))
}

export const allergySchema = z.object({
  allergen: requiredText(200),
  reaction: optionalText(500),
  severity: z.string(),
  notes: optionalText(4000),
})

export type AllergyValues = z.infer<typeof allergySchema>

export function allergyValuesOf(allergy?: Allergy): AllergyValues {
  return {
    allergen: allergy?.allergen ?? '',
    reaction: allergy?.reaction ?? '',
    severity: allergy?.severity ?? '',
    notes: allergy?.notes ?? '',
  }
}

export function toAllergyRequest(values: AllergyValues): AllergyRequest {
  return {
    allergen: values.allergen,
    reaction: emptyToNull(values.reaction),
    severity: values.severity === '' ? null : (values.severity as AllergySeverity),
    notes: emptyToNull(values.notes),
  }
}

export function conditionSchema(today: string) {
  return z.object({
    name: requiredText(200),
    description: optionalText(4000),
    status: z.string(),
    diagnosedOn: pastDate(today),
  })
}

export type ConditionValues = z.infer<ReturnType<typeof conditionSchema>>

export function conditionValuesOf(condition?: Condition): ConditionValues {
  return {
    name: condition?.name ?? '',
    description: condition?.description ?? '',
    status: condition?.status ?? 'ACTIVE',
    diagnosedOn: condition?.diagnosedOn ?? '',
  }
}

export function toConditionRequest(values: ConditionValues): ConditionRequest {
  return {
    name: values.name,
    description: emptyToNull(values.description),
    status: values.status as ConditionStatus,
    diagnosedOn: emptyToNull(values.diagnosedOn),
  }
}

export const medicationSchema = z
  .object({
    name: requiredText(200),
    dosage: optionalText(100),
    frequency: optionalText(100),
    notes: optionalText(4000),
    startDate: z.string(),
    endDate: z.string(),
    active: z.boolean(),
  })
  .check(
    z.superRefine((values, ctx) => {
      if (values.startDate && values.endDate && values.endDate < values.startDate) {
        ctx.addIssue({ code: 'custom', message: 'END_BEFORE_START', path: ['endDate'], input: values })
      }
    }),
  )

export type MedicationValues = z.infer<typeof medicationSchema>

export function medicationValuesOf(medication?: Medication): MedicationValues {
  return {
    name: medication?.name ?? '',
    dosage: medication?.dosage ?? '',
    frequency: medication?.frequency ?? '',
    notes: medication?.notes ?? '',
    startDate: medication?.startDate ?? '',
    endDate: medication?.endDate ?? '',
    active: medication?.active ?? true,
  }
}

export function toMedicationRequest(values: MedicationValues): MedicationRequest {
  return {
    name: values.name,
    dosage: emptyToNull(values.dosage),
    frequency: emptyToNull(values.frequency),
    notes: emptyToNull(values.notes),
    startDate: emptyToNull(values.startDate),
    endDate: emptyToNull(values.endDate),
    active: values.active,
  }
}

export function historySchema(today: string) {
  return z.object({
    description: requiredText(4000),
    occurredOn: pastDate(today),
    notes: optionalText(4000),
  })
}

export type HistoryValues = z.infer<ReturnType<typeof historySchema>>

export function historyValuesOf(entry?: HistoryEntry): HistoryValues {
  return { description: entry?.description ?? '', occurredOn: entry?.occurredOn ?? '', notes: entry?.notes ?? '' }
}

export function toHistoryRequest(values: HistoryValues): HistoryEntryRequest {
  return { description: values.description, occurredOn: emptyToNull(values.occurredOn), notes: emptyToNull(values.notes) }
}

export const noteSchema = z.object({ content: requiredText(20000) })

export type NoteValues = z.infer<typeof noteSchema>

/** Client clocks may run slightly ahead; the server allows the same margin. */
const CLOCK_SKEW_MS = 5 * 60_000

/** `consultedAt` is a clinic-local "YYYY-MM-DDTHH:mm"; it is converted to an instant on submit. */
export function consultationSchema(timeZone: string | null) {
  return z
    .object({
      consultedAt: z.string().check(
        z.minLength(1, { error: 'REQUIRED', abort: true }),
        z.refine((value) => Date.parse(instantOf(value, timeZone)) <= Date.now() + CLOCK_SKEW_MS, {
          error: 'DATE_IN_FUTURE',
        }),
      ),
      reason: requiredText(500),
      diagnosis: optionalText(4000),
      clinicalNotes: optionalText(20000),
      treatmentNotes: optionalText(20000),
      followUpNotes: optionalText(4000),
      followUpOn: z.string(),
    })
    .check(
      z.superRefine((values, ctx) => {
        if (values.consultedAt && values.followUpOn && values.followUpOn < values.consultedAt.slice(0, 10)) {
          ctx.addIssue({ code: 'custom', message: 'BEFORE_CONSULTATION', path: ['followUpOn'], input: values })
        }
      }),
    )
}

export type ConsultationValues = z.infer<ReturnType<typeof consultationSchema>>

export function consultationValuesOf(consultedAt: string, consultation?: Consultation): ConsultationValues {
  return {
    consultedAt,
    reason: consultation?.reason ?? '',
    diagnosis: consultation?.diagnosis ?? '',
    clinicalNotes: consultation?.clinicalNotes ?? '',
    treatmentNotes: consultation?.treatmentNotes ?? '',
    followUpNotes: consultation?.followUpNotes ?? '',
    followUpOn: consultation?.followUpOn ?? '',
  }
}

export function toConsultationRequest(values: ConsultationValues, timeZone: string | null): ConsultationRequest {
  return {
    consultedAt: instantOf(values.consultedAt, timeZone),
    reason: values.reason,
    diagnosis: emptyToNull(values.diagnosis),
    clinicalNotes: emptyToNull(values.clinicalNotes),
    treatmentNotes: emptyToNull(values.treatmentNotes),
    followUpNotes: emptyToNull(values.followUpNotes),
    followUpOn: emptyToNull(values.followUpOn),
  }
}
