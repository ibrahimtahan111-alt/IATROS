import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { createQueryClient } from '../app/queryClient'
import { routes } from '../app/routes'

/** Renders the real route tree at a path, with the app's providers. */
export function renderRoute(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = createQueryClient()
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { ...view, router, queryClient }
}
