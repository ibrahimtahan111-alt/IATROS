# IATROS — Technical & Product Decisions

## 1. Purpose

This document records decisions that the other documents left open.

Each decision is final for V1 unless deliberately revised. When a decision changes, update this file and every affected document.

Decisions are chosen for long-term maintainability, scalability, security, and performance, while keeping V1 simple.

---

# 2. Backend Platform

## D-001 — Versions

* Java 21 (LTS)
* Spring Boot 4.x (Spring Framework 7, Jakarta EE 11, Hibernate 7)
* Maven (via the Maven wrapper)
* PostgreSQL 18 (minimum supported: 16)

## D-002 — Database migrations: Flyway

Flyway is the migration tool.

* Migrations are plain SQL in `backend/src/main/resources/db/migration`.
* Naming: `V{n}__{description}.sql` (e.g. `V1__create_clinic.sql`).
* A migration is never edited after it has been merged; create a new one instead.
* Hibernate never generates or alters the schema (`spring.jpa.hibernate.ddl-auto=validate`).

Rationale: plain SQL gives full access to PostgreSQL features required by IATROS (exclusion constraints, partial indexes, `citext`, `tstzrange`) and keeps migrations reviewable.

## D-003 — Primary keys: UUID v7

All entities use `uuid` primary keys generated as **UUID version 7** by the application.

* Time-ordered UUIDs keep B-tree indexes compact and inserts fast (unlike random UUID v4).
* Non-sequential IDs resist resource enumeration (`security.md` §37).
* IDs can be generated before persistence, which simplifies idempotency and future integrations.

Integer sequences must not be used for public-facing identifiers.

## D-004 — Time handling

* Instants are stored as `timestamptz` and handled in Java as `Instant` / `OffsetDateTime`.
* The JDBC/Hibernate time zone is UTC (`hibernate.jdbc.time_zone=UTC`).
* Each clinic has an IANA time zone (e.g. `Europe/Paris`) in clinic settings.
* Recurring doctor schedules are stored as `day_of_week` + local `time` and interpreted in the clinic's time zone. This handles daylight-saving transitions correctly.
* Appointments are stored as `start_at` / `end_at` (`timestamptz`), not as separate date and time columns.
* API serialization uses ISO-8601: instants with offset (`2027-04-12T08:30:00Z`), dates as `YYYY-MM-DD`, local times as `HH:mm`.
* The server's local time zone is never used implicitly.

## D-005 — Appointment conflict prevention

Conflicts are prevented at the database level with a PostgreSQL exclusion constraint (`btree_gist`) on `(doctor_id, tstzrange(start_at, end_at))` for blocking statuses.

Cancelled and no-show appointments do not block time.

The service layer also checks conflicts to return a friendly error, but the constraint is the final concurrency guarantee (`api.md` §38).

## D-006 — Pagination response

Paginated endpoints return the format documented in `api.md` §26 through a shared `PageResponse` DTO.

* Default page size: 20
* Maximum page size: 100 (larger values are clamped)
* Sort fields are whitelisted per endpoint; unknown fields are rejected with `400 INVALID_SORT`.

## D-007 — Clinic isolation enforced by composite foreign keys

Every clinic-owned table exposes `UNIQUE (clinic_id, id)`. Tables that reference clinic-owned rows carry `clinic_id` and use **composite foreign keys** (e.g. `appointment (clinic_id, patient_id) → patient (clinic_id, id)`).

The database therefore rejects any row that links data from two different clinics, even if application code is wrong. This is defense in depth; backend authorization remains mandatory.

Clinical child rows (allergies, medications, notes, …) inherit their clinic through the medical record and do not duplicate `clinic_id`.

## D-008 — Schema conventions

* Table names are singular snake_case. The user table is `app_user` (`user` is reserved in PostgreSQL).
* Enumerations are `varchar` columns with `CHECK` constraints holding the stable values (not PostgreSQL enum types), so values can be added with a simple migration.
* Mutable aggregates have a `version` column for optimistic locking (JPA `@Version`), preventing lost updates when two users edit the same record.
* `created_at` / `updated_at` default to `now()`; `updated_at` is maintained by the application.
* Specialties are identified by stable `code` values (e.g. `CARDIOLOGY`); display names are translated in the frontend.
* Patient search uses a generated `search_text` column with a `pg_trgm` GIN index for fast partial matching on name, phone, and email.
* Audit rows are protected by a trigger that rejects `UPDATE`, `DELETE`, and `TRUNCATE`.

## D-009 — Persistence model

* Entities reference other aggregates **by ID** (`UUID doctorId`), not with JPA associations (`@ManyToOne`). This avoids lazy-loading surprises and N+1 queries, keeps module boundaries explicit, and makes each query's cost visible. Read views that need joined data use dedicated queries or projections.
* Clinic isolation in the persistence layer is **explicit**: clinic-owned repositories expose clinic-scoped finders (`findByIdAndClinicId`), and services always pass the authenticated user's clinic. Hibernate's implicit `@TenantId` filtering is not used; it is easy to bypass unknowingly (native queries, `findById`) and hides the rule from code review.
* Tables without `clinic_id` (schedule periods, schedule exceptions, clinical entries) are reached only after resolving their parent (doctor or medical record) within the caller's clinic.
* Soft-deleted clinical entries are excluded from all normal reads with `@SQLRestriction("deleted_at IS NULL")`.
* Domain invariants that do not need other data live on the entity (e.g. `Appointment.changeStatus` enforces D-024; invalid transitions raise `422 INVALID_STATUS_TRANSITION`).
* Expected constraint violations are translated to API codes by constraint name (e.g. `appointment_no_overlap` → `409 APPOINTMENT_CONFLICT`, `app_user_email_uk` → `409 EMAIL_ALREADY_USED`). The name is read from the PostgreSQL server error, so this does not depend on localized messages.
* Email lookups cast the parameter to `citext` in native queries; a plain JDBC string parameter is `varchar` and would compare case-sensitively.

---

# 3. Authentication & Security

## D-010 — Authentication mechanism: server-side sessions

IATROS uses **Spring Security with server-side sessions** stored in PostgreSQL through **Spring Session JDBC**.

* The browser receives an opaque session ID in a cookie: `HttpOnly`, `Secure` (outside local development), `SameSite=Lax`.
* No authentication token is ever readable by JavaScript.
* Logout, deactivation, and password changes invalidate sessions immediately on the server.
* Sessions are stored in the database, so backend instances stay stateless and can scale horizontally. A Redis session store can replace JDBC later without code changes.
* Idle timeout: 30 minutes. Absolute lifetime: 12 hours.

JWT is not used for the browser application. It is harder to revoke, and storing it in the browser exposes it to XSS.

## D-011 — CSRF

Because authentication uses cookies, **CSRF protection stays enabled**.

* Spring Security `CookieCsrfTokenRepository` issues a readable `XSRF-TOKEN` cookie.
* The frontend sends it back in the `X-XSRF-TOKEN` header on state-changing requests.

## D-012 — Deployment topology

The frontend and API are served from the **same origin** in production. A reverse proxy serves the built frontend and forwards `/api/*` to the backend.

* In development, the Vite dev server proxies `/api` to `http://localhost:8080`.
* CORS is therefore not required in production. If enabled for a specific environment, origins must be explicit; wildcards are not allowed.

## D-013 — Password hashing

Use Spring Security's `DelegatingPasswordEncoder` with **Argon2id** as the default encoding, so the algorithm can be upgraded later without forcing password resets.

* Parameters: 16-byte salt, 32-byte hash, parallelism 1, 19 MiB memory, 2 iterations (OWASP minimum for Argon2id).
* Hashes are stored as `{argon2}...`; `{bcrypt}` hashes are still accepted, so imported accounts keep working.
* Password policy: 12–128 characters, length only, no composition rules (NIST SP 800-63B). The new password and its confirmation must match (`PASSWORD_MISMATCH`).

## D-014 — Error responses

Errors use **RFC 9457 Problem Details** (`application/problem+json`) with IATROS extensions.

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "fieldErrors": [
    { "field": "startAt", "code": "REQUIRED" }
  ],
  "instance": "/api/v1/appointments",
  "timestamp": "2027-04-12T10:30:00Z"
}
```

* `code` is the stable, machine-readable identifier the frontend translates.
* Field errors carry codes, not translated sentences.

Status code policy:

| Status | Use                                                                                      |
| ------ | ---------------------------------------------------------------------------------------- |
| 400    | Malformed request or field validation failure (`VALIDATION_ERROR`)                       |
| 401    | Not authenticated                                                                        |
| 403    | Authenticated, but the role may not use this operation at all                            |
| 404    | Resource does not exist, belongs to another clinic, or is outside the user's data scope  |
| 409    | State conflict (e.g. `APPOINTMENT_CONFLICT`, duplicate email)                            |
| 422    | Valid request that violates a business rule (e.g. `DOCTOR_UNAVAILABLE`, `INVALID_STATUS_TRANSITION`) |

**Enumeration rule:** a resource outside the user's clinic or authorized scope returns `404`, never `403`, so its existence is not revealed.

Responses never include `detail`, exception messages, SQL, stack traces, or echoed input values. Every response carries an `X-Request-Id` header (also logged), so a reported error can be traced without logging request content. The shared codes are listed in `api.md` §31.

## D-015 — Email delivery

Transactional emails (password reset, staff invitation) are sent through an `EmailSender` abstraction backed by SMTP (Spring Mail).

* Transport is selected by `iatros.mail.transport`: `smtp` (default, production) or `outbox`.
* Development and tests use the `outbox` transport: messages are kept in memory and, in development, written as text files to `backend/target/mail-outbox/`. No mail server is needed, and the emails are still never logged.
* Production: any SMTP provider configured through environment variables (`IATROS_SMTP_*`, `IATROS_MAIL_FROM`, `IATROS_APP_BASE_URL`). Sending runs off the request thread, so SMTP latency does not affect response times or reveal whether an account exists.
* Emails are localized (`en`/`fr`) using the recipient's preferred language, then the clinic default, then English.

## D-016 — Rate limiting

V1 applies in-application rate limiting (fixed window, in memory) to authentication endpoints. Exceeding a limit returns `429 RATE_LIMITED` with a `Retry-After` header.

| Endpoint                                       | Limits                                 |
| ---------------------------------------------- | -------------------------------------- |
| Login                                          | 30 / 15 min per IP; 10 / 15 min per email |
| Forgot password                                | 10 / 15 min per IP; 5 / hour per email |
| Reset password, accept invitation              | 20 / 15 min per IP                     |
| Change password                                | 10 / 15 min per user                   |

The limiter is per instance. It moves to the reverse proxy or a shared store when the backend runs on multiple instances.

## D-017 — Authentication flows

* **Login** is a JSON endpoint (`POST /api/v1/auth/login`), not form login. Every failure (unknown email, wrong password, invited/deactivated account, suspended clinic) returns the same `401 INVALID_CREDENTIALS`. The password is always verified before account status, so responses do not reveal account state.
* On login the session ID and the CSRF token are rotated. The session records its authentication time; a filter enforces the 12-hour absolute lifetime, and Spring Session enforces the 30-minute idle timeout.
* Cookies: session `IATROS_SESSION` (`HttpOnly`, `SameSite=Lax`, `Secure` outside local development); CSRF `XSRF-TOKEN` (readable by JavaScript, `SameSite=Lax`). Cookie attributes are configured in code (`SessionConfig`, `SecurityConfig`) and covered by tests.
* The SPA calls `GET /api/v1/auth/csrf` once at start-up to obtain the CSRF cookie before its first state-changing request.
* **Single-use tokens** (invitation 72 hours, password reset 1 hour) are 256-bit random values. Only their SHA-256 hash is stored. Issuing a new token invalidates the user's previous unused token for the same purpose. Invalid, expired, and used tokens return the same `400 INVALID_TOKEN`.
* Token links put the token in the URL fragment (`/reset-password#token=...`), so it is not sent to servers or written to proxy logs.
* Change password invalidates the user's other sessions; reset password invalidates all of them.
* Login success/failure, logout, password change/reset, and invitation events are written to `audit_event`, without passwords or tokens.
* The session principal holds only user ID, clinic ID, and role. Any change to a user's role or status must invalidate that user's sessions (`UserSessionService`), so a stale principal never outlives a permission change.
* **Development seed:** with the `dev` profile and `IATROS_DEMO_PASSWORD` set, a fictional demo clinic (owner, doctor, secretary, patients, schedules, appointments) is created at start-up unless the demo owner account already exists. The seed never runs in other profiles and is not a migration.

## D-018 — Authorization layers

Authorization has two layers, and every protected operation passes both:

1. **Role** — `@PreAuthorize` on controller methods for operations a role may never use (e.g. staff management is owner-only). Failure: `403 FORBIDDEN`.
2. **Data scope** — the `access.AccessPolicy` service, called by services before reading or changing clinic data:

| Check                       | Owner                | Doctor                                 | Secretary              |
| --------------------------- | -------------------- | -------------------------------------- | ---------------------- |
| `requirePatient`            | Any clinic patient   | Primary or authorized patients         | Any clinic patient (booking DTO only, D-029) |
| `requireClinicalAccess`     | Any clinic patient   | Primary or authorized patients         | `403`                  |
| `requireAppointment`        | Any in clinic        | Own appointments                       | Any in clinic          |
| `requireDoctorCalendar`     | Any clinic doctor    | Self                                   | Any clinic doctor      |
| `requireDoctorManagement`   | Any clinic doctor    | Self                                   | `403`                  |
| `requireCanBook` (D-027)    | Any doctor/patient   | Self, authorized patients              | Any doctor/patient     |
| `dataScope` (lists, search, KPIs) | Clinic-wide    | Own doctor ID                          | Clinic-wide            |

* Lookups always use the caller's clinic (`findByIdAndClinicId`); IDs from another clinic behave as nonexistent.
* Out-of-scope resources return `404 <RESOURCE>_NOT_FOUND`, never `403` (D-014 enumeration rule). `403` is used only when the role can never perform the operation.
* Clinical data (medical record, clinical entries, consultations, documents) is always authorized through the owning patient with `requireClinicalAccess`.
* List and search queries take a `DataScope` and filter in SQL; results are never fetched broadly and filtered afterwards.

---

# 4. Product Rules

## D-020 — Clinic and owner onboarding

V1 has **no public self-signup**. Public signup is tied to subscription billing, which is deferred.

A clinic and its owner account are provisioned by the platform operator:

* Development: a fictional demo clinic is created by a development-only seed (never in production migrations).
* Production: a provisioning command creates the clinic, its settings, and the owner, then sends the owner an account-activation email.

The provisioning command runs the backend without its web server (usage in `README.md`):

```text
java -jar backend-<version>.jar provision-clinic --clinic-name="..." --time-zone=Europe/Paris --language=fr \
  --owner-first-name="..." --owner-last-name="..." --owner-email=... [--owner-specialty=GENERAL_PRACTICE]
```

All inputs are validated before anything is written, and the whole clinic is created in one transaction. `--owner-specialty` also creates a doctor profile for the owner (D-033). The event is audited as `CLINIC_PROVISIONED` with no actor.

The data model must not prevent adding self-signup later.

## D-021 — Staff account activation

1. The owner creates a team member (no password is set by the owner).
2. The system emails a single-use, expiring invitation link (72 hours).
3. The team member sets their password and the account becomes `ACTIVE`.

User status values: `INVITED`, `ACTIVE`, `DEACTIVATED`.

Invitation and reset tokens are stored only as hashes (SHA-256) and are single-use.

## D-022 — Doctor creation

There is **one** creation endpoint for clinic members: `POST /api/v1/staff`. When the role is `DOCTOR`, the request includes the doctor profile, and the user and doctor profile are created in one transaction.

`/api/v1/doctors` is read-oriented (listing, profiles, patients) plus doctor-profile updates.

## D-023 — Appointment types

Stable values (V1, not configurable per clinic):

```text
NEW_PATIENT
FOLLOW_UP
ROUTINE
PROCEDURE
URGENT
OTHER
```

Clinic-configurable types may be introduced later without changing stored values.

## D-024 — Appointment status transitions

```text
SCHEDULED       → CONFIRMED, WAITING, CANCELLED, NO_SHOW
CONFIRMED       → WAITING, CANCELLED, NO_SHOW
WAITING         → IN_CONSULTATION, CANCELLED
IN_CONSULTATION → COMPLETED
COMPLETED       → (terminal)
CANCELLED       → (terminal)
NO_SHOW         → (terminal)
```

* Only `SCHEDULED` and `CONFIRMED` appointments can be edited or rescheduled.
* `IN_CONSULTATION` and `COMPLETED` can be set only by the owner or the appointment's doctor.
* Every status change records who made it and when.
* Blocking statuses for conflict detection: `SCHEDULED`, `CONFIRMED`, `WAITING`, `IN_CONSULTATION`, `COMPLETED`.

## D-025 — Booking outside the schedule

There is no override flag. To book outside normal hours, an authorized user first adds an **extra-availability schedule exception**. Availability always comes from the schedule plus its exceptions.

## D-026 — Schedule exceptions are in V1

Schedule exceptions are part of V1 and are enforced by the backend.

Types:

* `UNAVAILABLE` — the doctor is unavailable (vacation, absence, holiday); full day or a time range.
* `EXTRA_AVAILABILITY` — additional working hours on a specific date.

## D-027 — Who can book

| Role      | Can book for                                                  |
| --------- | ------------------------------------------------------------- |
| Owner     | Any doctor, any clinic patient                                |
| Secretary | Any doctor, any clinic patient                                |
| Doctor    | Only themselves, only for patients they are authorized for    |

## D-028 — Patient status

```text
ACTIVE
ARCHIVED
```

Archived patients are hidden from default lists but keep their full history and can be restored.

## D-029 — Secretary patient data scope

Secretaries receive the booking-oriented patient DTO only:

* Name, date of birth, gender (if recorded)
* Phone, email, address, emergency contact
* Primary doctor
* Status
* Last and next appointment

They never receive allergies, conditions, medications, history, notes, consultations, diagnoses, or documents.

## D-030 — Deletion policy

| Entity                                 | Policy                                                         |
| -------------------------------------- | -------------------------------------------------------------- |
| User / staff                           | Deactivate only                                                 |
| Patient                                | Archive only                                                    |
| Appointment                            | Cancel only (never deleted)                                     |
| Consultation                           | Never deleted; edits are audited                                |
| Allergy, condition, medication, history entry, note | Soft delete (`deleted_at`, `deleted_by`) with audit; hidden from normal views |
| Document                               | Archive (soft delete); the file is retained                    |
| Schedule periods and exceptions        | Hard delete allowed (they are configuration, not history)      |
| Audit records                          | Never modified or deleted through the application              |

## D-031 — V1 KPI definitions

KPIs are calculated from authoritative records over a selected date range (in the clinic's time zone):

* **Appointments** — count by status.
* **Completion rate** — `COMPLETED / (COMPLETED + NO_SHOW + CANCELLED)` for appointments whose start is in the past.
* **No-show rate** — `NO_SHOW / (COMPLETED + NO_SHOW)`.
* **Cancellation rate** — `CANCELLED / all appointments`.
* **New patients** — patients created in the range.
* **Active patients** — patients with at least one completed appointment in the last 12 months.
* **Consultations** — consultations recorded in the range (owner: per doctor; doctor: own).

Doctors see only KPIs computed from their own data.

## D-032 — Document storage

Files are stored through a `DocumentStorage` abstraction. Files are never stored in database columns.

* Development: local filesystem directory outside the source tree.
* Production: S3-compatible object storage.

Defaults: maximum 20 MB per file; allowed types PDF, JPEG, PNG, WEBP. The content type is verified from the file signature, not only from the extension. Files are stored under generated UUID names.

## D-033 — The owner can also be a doctor

Each user has exactly one role, but a doctor profile can belong to a user whose role is `DOCTOR` **or** `OWNER`.

This supports the primary target user: a solo doctor who owns the clinic and also sees patients. An owner with a doctor profile appears in calendars, schedules, and patient-doctor relationships like any other doctor, while keeping owner permissions.

## D-034 — Staff management rules

* **Role is fixed after creation** in V1. Changing someone's role means deactivating the account and inviting them again, so permissions and history never shift silently.
* **Email** is the sign-in identity. It can be corrected only while the member is `INVITED`; the invitation is then re-sent to the new address and earlier links stop working. Once activated it cannot be changed by the owner (`EMAIL_CHANGE_NOT_ALLOWED`).
* **The owner cannot be deactivated** (`OWNER_STATUS_LOCKED`); a clinic always keeps its administrator.
* **Deactivating a doctor is blocked while they have upcoming appointments** (`SCHEDULED`, `CONFIRMED`, `WAITING`, or `IN_CONSULTATION` that have not ended). They must be cancelled or moved first, so no patient keeps a booking with a doctor who can no longer sign in.
* **Deactivation takes effect immediately:** all of the member's sessions end and their open invitation and reset links are revoked. Their history (appointments, consultations, audit) is kept.
* **Reactivation** restores `ACTIVE`, or `INVITED` if the member never set a password.
* **Invitations can be resent** by the owner, at most 5 times per member per hour; each resend invalidates earlier links.
* **Doctor profile edits** (specialties, registration number) belong to the doctor API (`PUT /doctors/{id}`): owners for any doctor, doctors for themselves. The staff API edits contact details only.

## D-035 — Doctor schedule rules

* **The week is saved whole.** `PUT /doctors/{id}/schedule` replaces every period in one transaction; there are no per-period endpoints. The editor always holds a complete week, so validation sees all periods at once and there are no partial states.
* **Separate schedule version.** `doctor.schedule_version` guards the week (stale → `409 CONCURRENT_MODIFICATION`). It is independent of the doctor row's `version`, so a profile edit and a schedule edit never conflict with each other.
* **Breaks are gaps.** A day has any number of periods; the time between them is a break. Periods of the same day may touch (12:00–14:00 then 14:00–18:00) but never overlap. A period ends after it starts and stays within one day (no overnight periods). Times are minute-precision clinic-local times (D-004), so daylight-saving changes do not shift working hours.
* **Who changes schedules.** Owners for any clinic doctor and doctors for themselves. Secretaries read availability (they book) but do not change schedules.
* **Exceptions** (D-026) cannot be created for past dates (clinic time zone). An `UNAVAILABLE` exception without times blocks the whole day; `EXTRA_AVAILABILITY` always has times. Several exceptions may exist on one date.
* **Effective working hours** for a date = union of that weekday's periods and its extra availability, minus its unavailability. An all-day unavailability wins over everything. A deactivated doctor has no working hours; the schedule is kept for a possible reactivation.
* **Schedule changes never move or cancel existing appointments.** Appointments outside the new hours stay as they are; the calendar shows them (Phase 9).
* **One calculation.** `WorkingHoursService` is the only implementation of availability. The working-hours endpoint and booking validation (`isAvailable`, used from Phase 9: an appointment must fit inside one working range of a single local date) both use it; the frontend never computes availability.

## D-036 — Patient management rules

* **Every patient always has exactly one primary doctor**, chosen at creation. It must be a doctor of the clinic whose account is not deactivated (invited doctors are allowed). Other doctors get access only through the owner.
* **Who creates patients.** Every role. A doctor can only create patients with themselves as primary doctor, so they never create a patient they cannot see. The patient's medical record is created in the same transaction.
* **Who edits.** Identity and contact details can be edited by every role within its patient scope (D-029); edits send the loaded `version`. Doctor access and status are not part of the edit.
* **Doctor access is owner-only.** The owner changes the primary doctor, gives other doctors access (`AUTHORIZED`), and removes it. Doctors cannot grant or remove access themselves in V1.
* **Changing the primary doctor keeps the previous one authorized**, so a doctor does not silently lose access to a patient they treated; the owner removes them explicitly if needed. The primary doctor itself cannot be removed (`PRIMARY_DOCTOR_REQUIRED`): another one is assigned first. Links only grant access; changing them never touches the medical record (AGENTS.md §7).
* **Archiving is owner-only** and blocked while the patient has upcoming appointments (`PATIENT_HAS_UPCOMING_APPOINTMENTS`), mirroring D-034 for doctors. Archived patients are read-only (`PATIENT_ARCHIVED`) — no edits or access changes — until restored. Doctors keep read access to archived patients they are linked to.
* **Birth dates** are calendar dates from 1900-01-01 up to today in the clinic's time zone.
* **Not in Phase 7:** duplicate-patient detection, and the last/next appointment columns of the list (they arrive with appointments in Phase 9).

## D-037 — Medical record and consultation rules

* **Who has clinical access.** The owner for every clinic patient, and doctors for the patients they are primary or authorized for. Secretaries never (`403 FORBIDDEN`); an unlinked doctor or another clinic's patient is `404 PATIENT_NOT_FOUND`. Access follows the current doctor links, so granting or removing access takes effect immediately without touching the record.
* **Structured sections** (allergies, chronic conditions, medications, past history) are maintained by anyone with clinical access, because they describe the patient, not one doctor's opinion. Updates require the loaded `version`; removal is a soft delete (D-030) that hides the entry from every reader. `GET /medical-record` returns all four sections in reading order; the record has no fields of its own, so there is no `PUT /medical-record`.
* **General notes** are signed statements: anyone with clinical access adds them, only the author edits or removes them (`403 FORBIDDEN` otherwise). They are paginated, newest first, and show their author and an "edited" marker.
* **Consultations** are recorded by a doctor as themselves: the doctor is the caller's own doctor profile, never a request field, so an owner without a doctor profile cannot record one (`403`). Only the consultation's doctor edits it; consultations are never deleted. Every doctor linked to the patient reads all of the patient's consultations, since continuity of care needs the full history.
* **Dates.** Condition diagnosis dates, history dates, and consultation times cannot be in the future (clinic time zone; five minutes of clock skew allowed for consultations). A follow-up date cannot be before the consultation's clinic-local date (`BEFORE_CONSULTATION`). A medication cannot end before it starts.
* **Archived patients' records are read-only** (`422 PATIENT_ARCHIVED`); linked clinicians still read them.
* **Audit.** Every create, update, and removal is recorded with identifiers only (`patientId`, and the names of the changed consultation fields); clinical content never enters the audit trail or logs (security.md).
* **Not in Phase 8:** linking a consultation to an appointment (Phase 9), documents (Phase 11), and specialty-specific fields (out of V1 scope).

## D-038 — Appointment and calendar rules

* **Bookings fit the schedule.** An appointment must lie inside one of the doctor's working ranges for its clinic-local date, exceptions included (`DOCTOR_UNAVAILABLE`, D-025), must not overlap a blocking appointment (`APPOINTMENT_CONFLICT`, D-005), and cannot start in the past (five minutes of clock skew). The same checks run when rescheduling, ignoring the appointment itself.
* **Status changes have their own endpoint**, separate from edits, so details and lifecycle never change in one request. `CANCELLED` is only reached through the cancel endpoint, which records an optional reason. Day-of statuses (`WAITING`, `IN_CONSULTATION`, `COMPLETED`, `NO_SHOW`) cannot be set before the appointment's clinic-local date (`APPOINTMENT_NOT_STARTED`).
* **The server tells the client what is allowed.** Appointment responses carry `editable`, `cancellable`, and `nextStatuses` for the caller, so the interface never re-implements D-024 or the role rules.
* **Archived patients.** Their appointments cannot be booked, edited, or rescheduled, but can still be cancelled or move through their status, so the day's work can be closed.
* **Appointment notes are administrative** and visible to every role that sees the appointment, secretaries included; clinical content belongs in the consultation. `consultationId` is never sent to secretaries.
* **Data scope.** Doctors see and book only their own appointments (D-027). The patient list's `nextAppointmentAt` and `lastVisitAt` are computed within the same scope.
* **Consultation link.** A consultation may record one appointment: the doctor's own, of the same patient, with a blocking status. One consultation per appointment (`CONSULTATION_ALREADY_EXISTS`). Starting a consultation from the calendar prefills its reason.
* **Calendar reads.** One request per view returns events and the shown doctors' working hours; the range is at most 42 days (six weeks, the month view). Deactivated doctors appear only when they have events in the range. Cancelled appointments are returned and hidden by default behind a "Show cancelled" toggle.
* **Availability** offers start times every 15 minutes inside working ranges. It is a suggestion; booking re-validates everything.
* **No date library.** Calendar math uses small native helpers on clinic-local `YYYY-MM-DD` dates and `Intl`, keeping the bundle small; this replaces the date-fns plan in D-040.

## D-039 — Dashboard, KPI, and activity rules

* **Who sees what.** The owner dashboard and clinic KPIs are owner-only. The doctor dashboard is for doctors, and for owners with a doctor profile (D-033); an owner without one gets `403`. Doctor KPIs go through the same check as doctor management: a doctor reads only their own (another doctor is `404 DOCTOR_NOT_FOUND`), the owner reads any clinic doctor's. Secretaries have no dashboard, KPIs, or activity feed (`403 FORBIDDEN`); their home page stays the calendar.
* **One request per dashboard.** Each dashboard is a single aggregated response (today's summary, schedule, per-doctor day, recent activity). Today is the clinic-local date. The owner's schedule is capped at 100 appointments (`scheduleTruncated`); the calendar remains the full view.
* **Clinical alerts** (names of severe allergies and active chronic conditions) appear on the doctor dashboard schedule, only for patients the doctor is linked to, so the doctor sees them before the visit. The owner dashboard shows no clinical data.
* **KPI periods.** `from`/`to` are inclusive clinic-local dates; the default is the last 30 days ending today, the maximum is 366 days (`OUT_OF_RANGE`), and `to` before `from` is `END_BEFORE_START`. Every response also carries the previous period of the same length, so the interface shows comparisons without a second request.
* **KPI definitions** follow D-031. Rates are `null` when their denominator is 0 (shown as "—", never as 0 %). A doctor's new patients are the patients created in the period with that doctor as primary. Active patients are counted over the 12 months ending at the period end. Clinic KPIs include a per-doctor table: active doctors, plus deactivated doctors who have data in the period.
* **Trend.** Daily buckets for periods up to 62 days, otherwise monthly; empty buckets are returned as zeros. Buckets use the clinic time zone.
* **KPIs are computed in SQL** (aggregates over indexed columns), never by loading rows into memory.
* **Activity comes from the audit trail.** Each audit action belongs to one type: `APPOINTMENTS`, `PATIENTS`, `CLINICAL`, `SCHEDULES`, `TEAM`, `CLINIC`. Security events (sign-ins, sign-outs, password changes and resets) belong to none and never appear in the feed. Items carry identifiers, names, and the new appointment status only — never clinical content, because the audit trail does not contain any (D-037).
* **Activity scope.** The owner sees the whole clinic, optionally filtered to one doctor. A doctor sees events they performed, events about their appointments, patients, or schedule (`doctorId` in the event), and events about their own doctor profile. Patient names are included only for patients the caller may see; otherwise the item shows no patient.
* **Overview and activity.** They are two pages. Overview is today's pulse and is listed first. Activity holds the period report (7, 30, 90, or 365 days ending today): KPIs, trend, per-doctor table, and the feed, with the doctor and activity-type filters. A switch at the end of the context bar of both pages links them. The current page, and the current side-menu item, are a light surface with a sage outline. Doctors use the same switch between My day and My activity.
* **Freshness.** Dashboards refresh every 60 seconds while open; there is no push channel in V1.
* **Document views** (`DOCUMENT_VIEWED`) are audited but belong to no type, so reading a file never appears in the feed (D-044).

## D-044 — Document and audit-detail rules

* **Access** is clinical access (D-037): the owner and the patient's linked doctors. Secretaries get `403`; an unlinked doctor gets `404 PATIENT_NOT_FOUND` on patient routes and `404 DOCUMENT_NOT_FOUND` on document routes, so a document ID never confirms that a hidden patient exists.
* **Accepted files:** PDF, JPEG, PNG, and WebP, at most 20 MB. The type is detected from the file's signature; the client's content type is ignored. The file name's extension must be an accepted one (`UNSUPPORTED_FILE_TYPE`) and must match the detected content (`FILE_TYPE_MISMATCH`), so a renamed executable or HTML file is rejected. Empty files are `FILE_EMPTY`; an oversized file is `FILE_TOO_LARGE` (`413` when the multipart limit stops it first). The stored content type is the detected one, so files are always served with a safe, verified type.
* **Storage.** File bytes never enter the database. They are stored under a generated UUIDv7 key, never under the user's file name, behind a `DocumentStorage` interface. V1 uses the local filesystem (`IATROS_DOCUMENTS_DIR`); an object-storage adapter (e.g. S3) is added at production deployment without changing the API. The database row keeps the cleaned display name, detected type, size, and SHA-256 checksum. If the transaction rolls back, the stored file is deleted.
* **Consultation attachments.** A document may name one consultation of the same patient (`consultationId` → `INVALID` otherwise). The consultation page lists and uploads only its attachments; the medical record lists them all.
* **Archive, never delete.** `DELETE /documents/{id}` archives: the document disappears from every list and lookup (`404`) but the file and row are kept for retention. Only the uploader or the owner may archive (`403` otherwise). Archived patients' documents stay readable, but nothing is uploaded or archived (`422 PATIENT_ARCHIVED`).
* **Every access is audited.** Upload and archive (`DOCUMENT_UPLOADED`, `DOCUMENT_ARCHIVED`, type `CLINICAL`) and each view or download (`DOCUMENT_VIEWED`, no type, kept out of the feed). Metadata is `patientId` and an optional `consultationId`; file names are never recorded because they can carry clinical meaning.
* **Changed-field names.** Updates to patients, staff, and consultations record the names of the fields that changed (`changedFields`), never their old or new values.

## D-045 — Browser hardening headers and the abuse matrix

* **API headers.** The backend sends a deny-everything `Content-Security-Policy` (`default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`), `Referrer-Policy: no-referrer`, `Cross-Origin-Resource-Policy: same-origin`, and a `Permissions-Policy` that disables camera, microphone, geolocation, payment, and USB, on every response, alongside Spring Security's defaults (`nosniff`, `X-Frame-Options: DENY`, no-store caching, HSTS over HTTPS). No `sandbox` directive, because it breaks built-in PDF viewers.
* **SPA headers** are the reverse proxy's job (D-012), with the policy given in `security.md` §27. The application ships no inline script or style, so the policy needs no `'unsafe-inline'` or hashes; the pre-paint theme script lives in `public/theme-init.js`.
* **Abuse matrix.** Cross-clinic and cross-role access is tested by `SecurityAbuseTests`, which enumerates every ID-based route from the application itself rather than from a hand-written list, and fails when a new body-taking route has no attack body (`security.md` §15).

## D-046 — Phone number entry

* **One stored value.** Phone fields keep their existing text columns and API fields (max 40). The interface stores `+<calling code> <number>` (e.g. `+30 690 000 0003`), the format the demo data and patient search already use, so no migration or API change is needed. The server still only checks the length; it does not validate numbers.
* **Country code select.** The country list and calling codes live in the frontend (`shared/lib/phoneNumbers.ts`), and country names come from `Intl.DisplayNames` in the interface language. A number typed or pasted with a leading `+<code>` moves that code into the select. Calling codes are prefix-free, so the first match while typing is correct.
* **Default country.** Empty fields start from the clinic phone's calling code, otherwise from the browser language's region. A stored number without a calling code is shown as is, with no country selected, and is not rewritten unless edited.
* **Shared codes** (`+1`, `+7`, `+44`, …) resolve to their main country (US, RU, GB, …) when a stored number is read. A country the user picks (e.g. Canada) is kept while editing.
* **Scope.** Patient and emergency-contact phones. Staff and clinic phone fields still use a plain text input.

---

# 5. Frontend Platform

## D-040 — Frontend libraries

| Concern                  | Choice                                                     |
| ------------------------ | ---------------------------------------------------------- |
| Routing                  | React Router (data router, lazy-loaded routes)             |
| Server state             | TanStack Query                                             |
| Internationalization     | i18next + react-i18next, namespaced JSON resources         |
| Styling                  | CSS Modules + CSS custom properties (design tokens)        |
| Accessible primitives    | Radix UI primitives via the unified `radix-ui` package (dialog, popover, menu, select, tabs, toast, tooltip), styled by IATROS |
| Forms & validation       | React Hook Form + Zod (`zod/mini`, Standard Schema resolver) |
| Icons                    | Lucide                                                     |
| Dates                    | Native `Intl` for formatting and time-zone conversion; small native helpers for calendar math (D-038) |
| Unit/component tests     | Vitest + Testing Library                                   |
| End-to-end tests         | Playwright                                                 |

Rationale:

* CSS Modules and CSS variables have no runtime cost and keep theme switching instant (a single `data-theme` attribute).
* Headless primitives provide accessibility while the visual identity remains fully IATROS's own (`design-system.md`).
* Route-level code splitting keeps the initial bundle small.

Libraries are added when the phase that needs them begins, not all at once.

## D-041 — TypeScript

`strict` mode is enabled. `any` requires a documented reason.

## D-042 — Frontend structure

```text
src/
├── app/            # providers, router, query client, app shell bootstrap
├── i18n/           # i18next setup + locales/{en,fr}/*.json
├── styles/         # design tokens, global styles, theme definitions
├── shared/
│   ├── api/        # HTTP client, error mapping, shared API types
│   ├── ui/         # design-system components
│   ├── hooks/
│   └── lib/        # formatting, small pure helpers
├── layouts/        # app shell, auth layout
└── features/
    ├── auth/
    ├── dashboard/
    ├── calendar/
    ├── patients/
    ├── medical-records/
    ├── team/       # staff list, add, member page
    ├── doctors/    # doctor profiles, specialties
    ├── schedules/
    ├── activity/
    ├── clinic/     # clinic settings page
    └── account/
```

Each feature owns its pages, components, API hooks, and types. Shared domain constants (for example appointment statuses) live in `shared/domain/`; shared form schema building blocks live in `shared/forms/`.

## D-043 — Frontend session, CSRF, and preferences behavior

* **Session state** is the TanStack Query entry `['auth','me']` (`GET /auth/me`; `401` means signed out). Any query or mutation that receives `401 UNAUTHENTICATED` clears it, which sends the user to `/login` with the requested path kept in router state. After sign-in the user returns to that path if it is an in-app path (`//` and absolute URLs are rejected), otherwise to the role home: secretaries `/calendar`, others `/dashboard`. This redirect is done by the `RedirectIfAuthenticated` guard alone.
* **Sign-in and sign-out replace the session in place:** the `['auth','me']` entry is updated and every other cached query is removed, so no data from the previous account survives. The entry is never removed itself, because guards observing it must re-render in the same pass as the pages below them. Signing out also leaves a sign-out marker in the cache, so the guard does not keep the current path: whoever signs in next starts from their own home page, never the previous user's last screen. Only an expired session keeps the path.
* **CSRF:** the HTTP client (`shared/api/http.ts`) reads the `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` on every non-GET request. If the cookie is missing it calls `GET /auth/csrf` first. On `403 CSRF_TOKEN_INVALID` it refreshes the token and retries once.
* **Route guards** (`RequireAuth`, `RequireRole`) only shape the UI; a role without access to a module sees a "not available" state. The backend authorizes every request (D-018).
* **Language:** stored locally only when the user explicitly picks one. When signed in, the choice is also saved to the account (`PUT /auth/me/preferences`). On sign-in the account preference wins; with no account preference and no local choice, the clinic default applies.
* **Theme** is a per-device preference stored locally (no server field).
* **Login bundle budget** (`design-system.md` §17): tooltip and toast providers are mounted in the signed-in shell, and the sign-in and forgot-password forms validate with the shared `zod/mini` schemas through a small `useSchemaForm` hook instead of React Hook Form. Other forms use React Hook Form.
* Error codes from the API are translated through the `errors` namespace (`errors:<CODE>`, field codes under `errors:field.<CODE>`); unknown codes fall back to a status-based message (`404` → `NOT_FOUND`, `5xx` → `INTERNAL_ERROR`, otherwise a generic one), so per-entity not-found codes need no translation of their own. Server field errors are shown next to their fields and the first one is focused (`shared/api/formErrors.ts`).
* **Translation namespaces:** the English `common`, `auth`, and `errors` ship with the app. Their French versions are fetched by a small i18next backend (`i18n/index.ts`) when French is first used: the app renders only once the initial language is loaded, and `changeLanguage` resolves after the fetch, so no untranslated text appears. Feature namespaces (`account`, `team`, `clinic`, `specialties`, `medicalRecords`, …) are loaded in both languages by the route that needs them (`app/lazyPage.ts`).
* **List state in the URL:** search (`q`), filters, sort, page (one-based in the URL), and page size are kept in the query string (`shared/hooks/useListSearchParams.ts`), so refresh and back navigation preserve them. Any change other than the page returns to the first page.
* **Unsaved changes:** full-page forms hold in-app navigation behind a confirmation and trigger the browser prompt on reload or close (`useUnsavedChangesGuard`). A page with several forms owns a single guard, since the router supports one blocker at a time.
* **Optimistic locking:** edit requests send the `version` they loaded. On `409 CONCURRENT_MODIFICATION` the form keeps the user's input, shows the message, and refetches so the next save uses the current version.

---

# 6. Testing Infrastructure

## D-050 — Backend tests use real PostgreSQL

Backend integration tests run against PostgreSQL, never H2, because IATROS relies on PostgreSQL-specific features.

* Preferred: Testcontainers (when Docker is available, and always in CI).
* Local fallback: a dedicated `iatros_test` database configured through environment variables.
