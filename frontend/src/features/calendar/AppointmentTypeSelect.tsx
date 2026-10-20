import { useTranslation } from 'react-i18next'
import type { FieldControlProps } from '../../shared/ui/Field'
import { Select } from '../../shared/ui/Select'
import { APPOINTMENT_TYPES, type AppointmentType } from './api'

interface AppointmentTypeSelectProps extends Partial<FieldControlProps> {
  value: AppointmentType
  onValueChange: (value: AppointmentType) => void
}

export function AppointmentTypeSelect({ value, onValueChange, ...control }: AppointmentTypeSelectProps) {
  const { t } = useTranslation('calendar')
  return (
    <Select
      {...control}
      value={value}
      onValueChange={(next) => onValueChange(next as AppointmentType)}
      options={APPOINTMENT_TYPES.map((type) => ({ value: type, label: t(`type.${type}`) }))}
    />
  )
}
