import { useEffect, useState } from 'react'

const TOKEN_PATTERN = /^[A-Za-z0-9_-]{16,128}$/

function readToken(): string | null {
  const value = new URLSearchParams(window.location.hash.slice(1)).get('token')
  return value && TOKEN_PATTERN.test(value) ? value : null
}

/**
 * Reads the single-use token from the link fragment (`#token=...`, decisions.md D-017) and removes it from the
 * address bar and history, so it is not left on screen or reused from the back button.
 */
export function useFragmentToken(): string | null {
  const [token] = useState(readToken)

  useEffect(() => {
    if (window.location.hash) {
      window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}`)
    }
  }, [])

  return token
}
