import { QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { RouterProvider, createBrowserRouter } from 'react-router'
import { watchSystemTheme } from '../shared/theme/theme'
import { createQueryClient } from './queryClient'
import { routes } from './routes'

const router = createBrowserRouter(routes)

export function App() {
  const [queryClient] = useState(createQueryClient)

  useEffect(() => watchSystemTheme(), [])

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
