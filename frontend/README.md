# IATROS Frontend

React + TypeScript (strict) + Vite.

See the root [README](../README.md) for setup, and [`docs/design-system.md`](../docs/design-system.md) and [`docs/decisions.md`](../docs/decisions.md) (D-040 to D-042) for UI rules, libraries, and folder structure.

## Scripts

| Command             | Purpose                                  |
| ------------------- | ---------------------------------------- |
| `npm run dev`       | Dev server on :5173, proxies `/api` to :8080 |
| `npm run build`     | Type-check and production build          |
| `npm run typecheck` | Type-check only                          |
| `npm run lint`      | ESLint                                   |
| `npm test`          | Vitest (includes EN/FR translation parity checks) |
