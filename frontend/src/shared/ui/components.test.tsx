import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import i18n from '../../i18n'
import { Button } from './Button'
import { Pagination } from './Pagination'
import { StatusIndicator } from './StatusIndicator'
import { TooltipProvider } from './Tooltip'

describe('Button', () => {
  it('keeps its label and blocks clicks while loading', async () => {
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        Save changes
      </Button>,
    )

    const button = screen.getByRole('button', { name: 'Save changes' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })
})

describe('Pagination', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en')
  })

  function renderPagination(page: number, totalElements: number, onPageChange = vi.fn()) {
    render(
      <TooltipProvider>
        <Pagination page={page} size={20} totalElements={totalElements} onPageChange={onPageChange} />
      </TooltipProvider>,
    )
    return onPageChange
  }

  it('shows the visible range and total', () => {
    renderPagination(1, 137)
    expect(screen.getByText('21–40 of 137')).toBeInTheDocument()
  })

  it('disables navigation past the first and last page', () => {
    renderPagination(0, 15)
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(screen.getByText('1–15 of 15')).toBeInTheDocument()
  })

  it('requests the next page', async () => {
    const onPageChange = renderPagination(0, 60)
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('formats the range for French', async () => {
    await i18n.changeLanguage('fr')
    renderPagination(0, 0)
    expect(screen.getByText('0–0 sur 0')).toBeInTheDocument()
  })
})

describe('StatusIndicator', () => {
  it('always shows the status as text, translated', async () => {
    render(<StatusIndicator status="IN_CONSULTATION" />)
    expect(screen.getByText('In consultation')).toBeInTheDocument()

    await i18n.changeLanguage('fr')
    expect(await screen.findByText('En consultation')).toBeInTheDocument()
    await i18n.changeLanguage('en')
  })
})
