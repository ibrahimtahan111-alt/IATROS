import { mkdirSync, writeFileSync } from 'node:fs'
import { test as setup } from '@playwright/test'
import { ApiSession, accounts, authDir, requireDemoClinic, savedLanguagesFile, stateOf, type Role } from './support.ts'

requireDemoClinic()

/**
 * Signs each role in once and switches it to English so tests can use English labels. The accounts' own language
 * choices are saved and put back by the teardown project.
 */
setup('sign in the demo accounts', async ({ baseURL }) => {
  mkdirSync(authDir, { recursive: true })
  const languages: Partial<Record<Role, string | null>> = {}
  for (const role of Object.keys(accounts) as Role[]) {
    const session = await ApiSession.signIn(baseURL!, accounts[role])
    const { preferredLanguage } = await session.get<{ preferredLanguage: string | null }>('/auth/me')
    languages[role] = preferredLanguage
    await session.send('PUT', '/auth/me/preferences', { preferredLanguage: 'en' })
    await session.saveState(stateOf(role))
    await session.dispose()
  }
  writeFileSync(savedLanguagesFile, JSON.stringify(languages))
})
