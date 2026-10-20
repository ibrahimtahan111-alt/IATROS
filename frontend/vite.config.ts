/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Route tests compile lazy page chunks on first render; with files running in parallel that can exceed 5 s.
    testTimeout: 15_000,
    // tokens.css is processed so its contrast test can read it.
    css: { include: [/tokens\.css/], modules: { classNameStrategy: 'non-scoped' } },
  },
})
