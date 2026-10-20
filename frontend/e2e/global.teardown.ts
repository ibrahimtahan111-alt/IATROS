import { existsSync, readFileSync } from 'node:fs'
import { test as teardown } from '@playwright/test'
import { ApiSession, savedLanguagesFile, type Role } from './support.ts'

/** Gives the demo accounts back the languages they had before the run. */
teardown('restore the demo accounts’ languages', async ({ baseURL }) => {
  if (!existsSync(savedLanguagesFile)) {
    return
  }
  const languages = JSON.parse(readFileSync(savedLanguagesFile, 'utf8')) as Record<Role, string | null>
  for (const [role, preferredLanguage] of Object.entries(languages) as [Role, string | null][]) {
    const session = await ApiSession.as(baseURL!, role)
    await session.send('PUT', '/auth/me/preferences', { preferredLanguage })
    await session.dispose()
  }
})
