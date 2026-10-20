import type { ComponentType } from 'react'
import { loadNamespaces } from '../i18n/loadNamespaces'
import type { LazyNamespace } from '../i18n/resources'

/** Route `lazy` handler that loads a page chunk and its translation namespaces in parallel. */
export function lazyPage(load: () => Promise<ComponentType>, namespaces: LazyNamespace[] = []) {
  return async () => {
    const [Component] = await Promise.all([load(), loadNamespaces(...namespaces)])
    return { Component }
  }
}
