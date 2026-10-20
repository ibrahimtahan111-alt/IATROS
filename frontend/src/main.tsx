import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/global.css'
import { i18nReady } from './i18n'
import { App } from './app/App'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Root element #root not found')
}

void i18nReady.then(() =>
  createRoot(rootElement).render(
    <StrictMode>
      <App />
    </StrictMode>,
  ),
)
