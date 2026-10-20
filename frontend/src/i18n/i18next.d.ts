import 'i18next'
import type { AllResources, defaultNamespace } from './resources'

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: typeof defaultNamespace
    resources: AllResources
  }
}
