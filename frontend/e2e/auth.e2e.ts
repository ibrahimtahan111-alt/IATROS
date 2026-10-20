import { expect, test } from '@playwright/test'
import { accounts, requireDemoClinic, signIn } from './support.ts'

requireDemoClinic()

test('rejects a wrong password without saying whether the account exists', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Email').fill('nobody@demo.iatros.test')
  await page.getByLabel('Password', { exact: true }).fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByRole('alert')).toContainText('The email or password is incorrect')
  await expect(page).toHaveURL(/\/login/)
})

test('signs the owner in to the requested page and out again', async ({ page }) => {
  await page.goto('/patients')
  await expect(page).toHaveURL(/\/login/)

  await signIn(page, accounts.owner)
  await expect(page).toHaveURL(/\/patients/)
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Overview' }).click()
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()

  await page.getByRole('button', { name: /Account menu/ }).click()
  await page.getByRole('menuitem', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login/)
})
