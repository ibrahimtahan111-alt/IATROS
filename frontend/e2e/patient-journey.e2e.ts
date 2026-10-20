import { expect, test } from '@playwright/test'
import { ApiSession, demoDoctorName, futureWeekday, pageAs, requireDemoClinic } from './support.ts'

/**
 * The core V1 workflow across roles: the secretary registers and books a patient, cannot open the medical record,
 * and the treating doctor records an allergy. The patient is archived afterwards so runs do not pile up.
 */
requireDemoClinic()
test.describe.configure({ mode: 'serial' })

const lastName = `E2e${Date.now().toString(36)}`
const fullName = `Ada ${lastName}`
let patientPath = ''

test.afterAll(async ({ baseURL }) => {
  if (!patientPath) {
    return
  }
  const owner = await ApiSession.as(baseURL!, 'owner')
  await cancelUpcomingAppointments(owner, patientPath.split('/').at(-1)!)
  await owner.send('PATCH', `${patientPath}/status`, { status: 'ARCHIVED' })
  await owner.dispose()
})

/** Upcoming appointments block archiving a patient, so clean-up cancels them first. */
async function cancelUpcomingAppointments(owner: ApiSession, patientId: string) {
  const { content } = await owner.get<{ content: { id: string; status: string }[] }>(
    `/appointments?patientId=${patientId}&status=SCHEDULED&status=CONFIRMED&size=100`,
  )
  for (const { id } of content) {
    const { version } = await owner.get<{ version: number }>(`/appointments/${id}`)
    await owner.send('POST', `/appointments/${id}/cancel`, { reason: null, version })
  }
}

test('the secretary registers a patient for the demo doctor', async ({ browser }) => {
  const page = await pageAs(browser, 'secretary')
  await page.goto('/patients/new')
  await page.getByLabel('First name').fill('Ada')
  await page.getByLabel('Last name').fill(lastName)
  await page.getByLabel('Date of birth').fill('1985-04-12')
  await page.getByRole('combobox', { name: 'Primary doctor' }).click()
  await page.getByRole('option', { name: demoDoctorName }).click()
  await page.getByRole('button', { name: 'Create patient' }).click()

  await expect(page.getByRole('heading', { name: fullName })).toBeVisible()
  patientPath = new URL(page.url()).pathname
  await expect(page.getByRole('link', { name: 'Medical record' })).toHaveCount(0)
})

test('the secretary books the patient into a free slot', async ({ browser }) => {
  const page = await pageAs(browser, 'secretary')
  await page.goto('/calendar')
  await page.getByRole('button', { name: 'Book appointment' }).click()
  const dialog = page.getByRole('dialog', { name: 'Book an appointment' })

  await dialog.getByRole('searchbox').fill(lastName)
  await dialog.getByRole('button', { name: new RegExp(fullName) }).click()
  await dialog.getByRole('combobox', { name: 'Doctor' }).click()
  await page.getByRole('option', { name: demoDoctorName }).click()
  await dialog.getByLabel('Date').fill(futureWeekday())
  await dialog.getByRole('group', { name: 'Free start times' }).getByRole('button').first().click()
  await dialog.getByRole('button', { name: 'Book' }).click()

  await expect(page.getByText('Appointment booked.').first()).toBeVisible()
  await expect(page).toHaveURL(/appointment=/)
  await expect(page.getByRole('dialog').getByText(fullName).first()).toBeVisible()
})

test('the secretary cannot open the medical record, even by URL', async ({ browser }) => {
  const page = await pageAs(browser, 'secretary')
  await page.goto(`${patientPath}/medical-record`)
  await expect(page.getByRole('heading', { name: 'This page is not available' })).toBeVisible()
})

test('the treating doctor records an allergy', async ({ browser }) => {
  const page = await pageAs(browser, 'doctor')
  await page.goto(patientPath)
  await page.getByRole('link', { name: 'Medical record' }).click()

  const allergies = page.locator('section').filter({ has: page.getByRole('heading', { name: 'Allergies' }) })
  await expect(allergies).toContainText('No known allergies recorded.')
  await allergies.getByRole('button', { name: 'Add' }).click()
  const dialog = page.getByRole('dialog', { name: 'Add an allergy' })
  await dialog.getByLabel('Allergen').fill('Latex')
  await dialog.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByText('Saved.').first()).toBeVisible()
  await expect(page.getByRole('list', { name: 'Allergies' })).toContainText('Latex')
})
