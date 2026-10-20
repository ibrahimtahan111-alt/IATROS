import { expect, test } from '@playwright/test'
import { requireDemoClinic, stateOf } from './support.ts'

requireDemoClinic()
test.use({ storageState: stateOf('owner') })

test('switches language for the account and theme for the device, both surviving a reload', async ({ page }) => {
  await page.goto('/account')
  const root = page.locator('html')

  await page.getByRole('combobox', { name: 'Language' }).click()
  await page.getByRole('option', { name: 'Français' }).click()
  await expect(page.getByRole('heading', { name: 'Mon compte' })).toBeVisible()
  await expect(root).toHaveAttribute('lang', 'fr')

  await page.getByRole('combobox', { name: 'Thème' }).click()
  await page.getByRole('option', { name: 'Sombre' }).click()
  await expect(root).toHaveAttribute('data-theme', 'dark')

  await page.reload()
  await expect(page.getByRole('heading', { name: 'Mon compte' })).toBeVisible()
  await expect(root).toHaveAttribute('data-theme', 'dark')

  await page.getByRole('combobox', { name: 'Langue' }).click()
  await page.getByRole('option', { name: 'English' }).click()
  await expect(page.getByRole('heading', { name: 'Account' })).toBeVisible()
  await page.getByRole('combobox', { name: 'Theme' }).click()
  await page.getByRole('option', { name: 'Match system' }).click()
  await expect(root).toHaveAttribute('data-theme', 'light')
})
