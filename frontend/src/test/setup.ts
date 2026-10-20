import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import '../i18n'

// Route modules are lazy; the first import of a chunk can exceed the 1s default on a cold transform.
configure({ asyncUtilTimeout: 5000 })

// jsdom lacks these browser APIs used by Radix primitives.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver
Element.prototype.scrollIntoView ??= () => {}
Element.prototype.hasPointerCapture ??= () => false

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  localStorage.clear()
  document.cookie.split('; ').forEach((cookie) => {
    const name = cookie.split('=')[0]
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
    }
  })
})
