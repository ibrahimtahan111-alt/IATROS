import { expect, request, test, type APIRequestContext, type Browser, type Page } from '@playwright/test'

/** Fictional demo accounts created by the backend's dev seeder (README). */
export const accounts = {
  owner: 'owner@demo.iatros.test',
  doctor: 'doctor@demo.iatros.test',
  secretary: 'secretary@demo.iatros.test',
} as const

export type Role = keyof typeof accounts

export const demoDoctorName = 'Nikos Petridis'

/**
 * Signed-in sessions saved by the setup project. Each role signs in once per run, which keeps the suite within the
 * login rate limits (security.md §33). The files hold session cookies and are git-ignored.
 */
export const authDir = 'e2e/.auth'
export const stateOf = (role: Role) => `${authDir}/${role}.json`
export const savedLanguagesFile = `${authDir}/languages.json`

const password = process.env.IATROS_DEMO_PASSWORD ?? ''

/** Skips the file when the demo password is not configured, instead of failing on sign-in. */
export function requireDemoClinic() {
  test.skip(password === '', 'Set IATROS_DEMO_PASSWORD to the demo clinic password to run end-to-end tests.')
}

/** Signs in through the form; only for tests about signing in, everything else reuses the saved sessions. */
export async function signIn(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

/** A page in a fresh browser context signed in as the role. */
export async function pageAs(browser: Browser, role: Role): Promise<Page> {
  const context = await browser.newContext({ storageState: stateOf(role) })
  return context.newPage()
}

/** A signed-in API client for setup and clean-up that the UI flow under test does not cover. */
export class ApiSession {
  private readonly context: APIRequestContext

  private constructor(context: APIRequestContext) {
    this.context = context
  }

  static async signIn(baseURL: string, email: string): Promise<ApiSession> {
    const session = new ApiSession(await request.newContext({ baseURL }))
    await session.context.get('/api/v1/auth/csrf')
    await session.send('POST', '/auth/login', { email, password })
    return session
  }

  static async as(baseURL: string, role: Role): Promise<ApiSession> {
    return new ApiSession(await request.newContext({ baseURL, storageState: stateOf(role) }))
  }

  async get<T>(path: string): Promise<T> {
    const response = await this.context.get(`/api/v1${path}`)
    expect(response.ok(), `GET ${path}`).toBe(true)
    return (await response.json()) as T
  }

  async send<T>(method: 'POST' | 'PUT' | 'PATCH', path: string, data: unknown): Promise<T> {
    const response = await this.context.fetch(`/api/v1${path}`, {
      method,
      data,
      headers: { 'X-XSRF-TOKEN': await this.csrfToken() },
    })
    expect(response.ok(), `${method} ${path}: ${response.status()}`).toBe(true)
    return (response.status() === 204 ? undefined : await response.json()) as T
  }

  async saveState(path: string) {
    await this.context.storageState({ path })
  }

  async dispose() {
    await this.context.dispose()
  }

  private async csrfToken(): Promise<string> {
    const { cookies } = await this.context.storageState()
    return decodeURIComponent(cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '')
  }
}

/** The first weekday at least two weeks ahead, when the demo doctor works and the calendar is still open. */
export function futureWeekday(): string {
  const date = new Date()
  date.setDate(date.getDate() + 14)
  while (date.getDay() === 0 || date.getDay() === 6) {
    date.setDate(date.getDate() + 1)
  }
  return date.toLocaleDateString('en-CA')
}
