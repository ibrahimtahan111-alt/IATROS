import type { FieldValues, UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { applyFieldErrors } from '../../shared/api/formErrors'
import { useToast } from '../../shared/ui/useToast'
import { useSaveEntry, type RecordSection, type SectionRequests } from './api'

/** Saves an add/edit dialog of a record section, then closes it; server field errors land on the form. */
export function useEntrySave<S extends RecordSection, V extends FieldValues>(
  patientId: string,
  section: S,
  entry: { id: string; version: number } | undefined,
  form: UseFormReturn<V>,
  onClose: () => void,
) {
  const { t } = useTranslation('medicalRecords')
  const toast = useToast()
  const save = useSaveEntry(patientId, section)

  const submit = (request: SectionRequests[S]) =>
    save.mutate(
      { id: entry?.id, version: entry?.version, request },
      {
        onSuccess: () => {
          toast(t('saved'))
          onClose()
        },
        onError: (error) => applyFieldErrors(error, form.setError, form.setFocus),
      },
    )

  return { submit, pending: save.isPending, error: save.error }
}
