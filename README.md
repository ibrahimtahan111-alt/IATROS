# IATROS

Web-based clinic-management SaaS for solo doctors and small clinics.

* **Frontend:** React + TypeScript + Vite (`frontend/`)
* **Backend:** Java 21 + Spring Boot 4 + Maven (`backend/`)
* **Database:** PostgreSQL with Flyway migrations

The product and technical source of truth lives in [`docs/`](docs). Start with [`docs/product.md`](docs/product.md), [`docs/decisions.md`](docs/decisions.md), and [`docs/design-system.md`](docs/design-system.md). AI assistants follow [`AGENTS.md`](AGENTS.md).

## Prerequisites

* Java 21
* Node.js 24+
* PostgreSQL 16+ (18 recommended)

## First-time setup

### 1. Create the local databases

Run once as a PostgreSQL superuser, choosing a local password:

```bash
psql -U postgres -v app_password='choose-a-local-password' -f backend/db/local-setup.sql
```

This creates the `iatros` role and the `iatros` (development) and `iatros_test` (tests) databases.

### 2. Configure environment variables

Copy `.env.example` to `.env` for reference and set the variables in your shell or IDE run configuration. At minimum:

```powershell
$env:IATROS_DB_PASSWORD = "choose-a-local-password"
```

To create a fictional demo clinic on the first `dev` start, also set a demo password (at least 8 characters):

```powershell
$env:IATROS_DEMO_PASSWORD = "choose-a-demo-password"
```

Uploaded documents are stored in `~/.iatros/documents` in `dev`; set `IATROS_DOCUMENTS_DIR` to use another directory (it is required in `prod`).

`.env` files are git-ignored. Never commit real credentials.

### 3. Install frontend dependencies

```bash
cd frontend
npm install
```

## Running locally

On Windows, double-click `start.bat` (or run `.\start.bat` from the repository root). It loads `.env`, opens the backend and frontend in separate windows, skips any server that is already running, and opens the app in your browser once the backend is healthy. Close a window to stop that server.

To start each server manually instead:

Backend (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Frontend (http://localhost:5173, proxies `/api` to the backend):

```bash
cd frontend
npm run dev
```

### Demo accounts (`dev` profile)

When `IATROS_DEMO_PASSWORD` is set, the backend creates a demo clinic with fictional patients, schedules, and this week's appointments. All accounts use the demo password:

| Email                         | Role                                   |
| ----------------------------- | -------------------------------------- |
| `owner@demo.iatros.test`      | Owner (also a general-practice doctor) |
| `doctor@demo.iatros.test`     | Doctor (cardiology)                    |
| `secretary@demo.iatros.test`  | Secretary                              |

The seed is skipped if the demo owner already exists.

### Emails in development

The `dev` profile does not send email. Password-reset and invitation emails are written as text files to `backend/target/mail-outbox/`; open the newest file to follow the link.

## Provisioning a clinic

There is no public sign-up (`docs/decisions.md` D-020). The operator creates each clinic and its owner with a one-off command that runs without starting the web server. The owner receives an activation email and then invites the rest of the team from **Team**.

```bash
cd backend
./mvnw -q package -DskipTests
java -jar target/backend-*.jar provision-clinic \
  --spring.profiles.active=prod \
  --clinic-name="Cabinet Martin" \
  --time-zone=Europe/Paris \
  --language=fr \
  --owner-first-name="Claire" \
  --owner-last-name="Martin" \
  --owner-email=claire.martin@example.test \
  --owner-specialty=GENERAL_PRACTICE
```

`--owner-specialty` is optional; when set, the owner also gets a doctor profile. Use the specialty codes from `GET /api/v1/specialties`. The command exits with a non-zero status and writes nothing if any input is invalid or the email is already in use.

## Checks

```bash
# Backend (requires the iatros_test database)
cd backend && ./mvnw test
# ...with a coverage report in backend/target/site/jacoco/index.html
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run typecheck && npm run lint && npm test
```

### End-to-end tests

Playwright drives the real application in Chromium against the fictional demo clinic:

1. Start the backend with the `dev` profile and `IATROS_DEMO_PASSWORD` set (see [Running locally](#running-locally)).
2. Install the browser once: `cd frontend && npx playwright install chromium`.
3. Run `npm run e2e` from `frontend/` with `IATROS_DEMO_PASSWORD` set in the same shell. Playwright starts the Vite dev server if none is running. If it runs on another port, set `E2E_BASE_URL` (for example `http://localhost:5174`).

Each role signs in once per run, which stays within the login rate limits. The demo accounts are switched to English for the run and their own languages are restored afterwards. The test patient that each run creates is archived at the end. Failure screenshots and traces are in `frontend/test-results/`, and the HTML report is in `frontend/playwright-report/`.

## Profiles

| Profile | Use                                                        |
| ------- | ---------------------------------------------------------- |
| `dev`   | Local development                                          |
| `test`  | Automated tests (activated by the test suite)              |
| `prod`  | Production; all connection settings come from environment variables |
