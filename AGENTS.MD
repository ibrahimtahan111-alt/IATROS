# IATROS — AI Development Instructions

## 1. Project

IATROS is a web-based clinic-management SaaS for solo doctors and small clinics.

The application is built as a modular monolith:

* Frontend: React + TypeScript + Vite
* Backend: Java + Spring Boot
* Database: PostgreSQL
* API: REST
* Build/backend dependency management: Maven

The project is designed to be secure, maintainable, extensible, and suitable for future specialty-specific clinical modules.

---

# 2. Read the Documentation First

Before implementing or significantly modifying a feature, read the relevant files in `docs/`.

The documentation is the project's source of truth.

Core documents:

```text
docs/product.md
docs/requirements.md
docs/roles-and-permissions.md
docs/architecture.md
docs/database.md
docs/modules.md
docs/ui-ux.md
docs/api.md
docs/security.md
docs/internationalization.md
docs/roadmap.md
docs/development-rules.md
```

Do not assume existing code is more authoritative than the documented requirements.

If code and documentation disagree, identify the conflict before proceeding.

---

# 3. Product Scope

IATROS V1 includes:

* Authentication
* Clinic settings
* Staff management
* Doctor management
* Roles and permissions
* Doctor specialties
* Doctor schedules
* Patient management
* Patient-doctor authorization
* Medical records
* Consultations
* Documents
* Calendar
* Appointments
* Availability
* Owner dashboard
* Doctor dashboard
* Clinic activity
* KPIs
* Search
* Filtering
* Sorting
* Pagination
* English
* French
* Light mode
* Dark mode

Do not silently add deferred functionality.

V1 does not include:

* Patient portal
* Patient accounts
* Online patient booking
* Mobile application
* Google Calendar integration
* Outlook integration
* Online payments
* Subscription billing
* Advanced insurance management
* Advanced laboratory management
* Advanced imaging management
* Advanced prescription management
* Specialty-specific clinical modules
* AI clinical functionality
* Multi-clinic management

---

# 4. Roles

IATROS V1 has three implemented roles:

```text
OWNER
DOCTOR
SECRETARY
```

## Owner

Full access to the clinic within the defined product scope.

## Doctor

Access is restricted to their permitted data and authorized patients.

A doctor must not automatically have access to:

* other doctors' restricted data
* owner/admin data
* clinic-wide administrative KPIs
* unauthorized patients
* unauthorized medical records
* unauthorized documents

## Secretary

Primarily handles:

* patient booking
* appointment operations
* calendar operations

Secretary does not have unrestricted medical-record access.

---

# 5. Authorization Is Backend-First

Never treat frontend visibility as security.

The frontend may hide or disable unavailable actions, but the backend must independently enforce:

* authentication
* role permissions
* clinic isolation
* patient authorization
* medical-record authorization
* appointment authorization
* document authorization

Never weaken authorization merely to make a feature work.

---

# 6. Clinic Isolation

Every clinic-scoped resource must be protected by the authenticated user's clinic context.

Never trust a client-provided `clinicId` as proof of authorization.

A manipulated ID, URL, query parameter, or request body must not allow cross-clinic access.

---

# 7. Patient Access

Patients belong to a clinic.

A patient has:

* one primary doctor
* zero or more additional authorized doctors

Doctor access must be based on the defined authorization relationship.

Changing a patient's doctor must not delete or recreate the patient's medical history.

---

# 8. Medical Records

Every patient has a medical record.

The generic medical-record foundation includes:

* Allergies
* Chronic conditions
* Current medications
* Past medical history
* General medical notes
* Consultations
* Diagnoses
* Clinical/treatment notes
* Follow-up information
* Medical documents

Keep the model generic.

Do not create specialty-specific patient schemas for V1.

Future specialty modules must extend the generic clinical foundation rather than replacing it.

---

# 9. Appointment Rules

Appointments are calendar-first.

Supported views:

* Day
* Week
* Month

Appointment status values must remain language-independent:

```text
SCHEDULED
CONFIRMED
WAITING
IN_CONSULTATION
COMPLETED
CANCELLED
NO_SHOW
```

The backend must enforce:

* doctor availability
* schedule exceptions
* appointment conflicts
* clinic ownership
* patient authorization
* valid appointment transitions

Never rely only on frontend validation.

---

# 10. Database Rules

PostgreSQL is the database.

All schema changes must use migrations.

Never:

* manually alter the production schema
* delete tables to fix development problems
* drop data casually
* bypass migrations

Database values must be language-independent.

For example:

```text
COMPLETED
```

not:

```text
Completed
```

or:

```text
Terminé
```

---

# 11. Backend Architecture

Follow:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Controllers should not contain substantial business logic.

Business rules belong in services/domain logic.

Repositories handle persistence.

Do not expose JPA entities directly as REST API contracts.

Use DTOs.

---

# 12. Frontend Architecture

Use:

```text
React
TypeScript
Vite
React Router
```

Keep components focused.

Avoid:

* giant components
* giant hooks
* giant services
* giant utility files
* duplicated API logic
* duplicated business rules

Use reusable components for repeated UI patterns.

Keep business-critical rules on the backend.

---

# 13. API Rules

Use the documented API structure:

```text
/api/v1/...
```

API contracts must use predictable:

* HTTP status codes
* DTOs
* validation
* machine-readable error codes

Never expose:

* stack traces
* secrets
* internal implementation details
* unnecessary sensitive data

---

# 14. Search, Filtering, Sorting & Pagination

Search, filtering, sorting, and pagination are global requirements.

For relevant datasets:

* operate only on authorized data
* prefer server-side pagination
* avoid loading unlimited records
* preserve appropriate filters
* provide clear reset behavior
* use appropriate database indexes

Never fetch unauthorized data and merely hide it in the UI.

---

# 15. Internationalization

IATROS supports:

```text
en
fr
```

No user-facing text should be hardcoded in React components.

Use translation keys.

Do not use translated strings as business identifiers.

User-generated content such as medical notes must not be automatically translated when the interface language changes.

---

# 16. Theme

Every UI feature must support:

* Light mode
* Dark mode

Do not create components that only work visually in one theme.

---

# 17. Security

Treat medical and clinic data as sensitive.

Never log:

* passwords
* tokens
* secrets
* medical notes
* unnecessary patient information

Uploaded files are untrusted input and must be validated and securely accessed.

Never place sensitive medical information in URLs unnecessarily.

---

# 18. Testing

Do not consider a feature complete merely because it works manually.

Test important business rules, especially:

* authentication
* authorization
* clinic isolation
* patient authorization
* medical-record access
* appointment conflicts
* doctor availability
* validation
* sensitive operations

Always include negative authorization cases for protected functionality.

---

# 19. AI Coding Behavior

When modifying the project:

1. Read relevant documentation first.
2. Inspect existing code before creating new code.
3. Reuse established patterns.
4. Keep changes focused.
5. Do not silently change product requirements.
6. Do not weaken security.
7. Do not duplicate existing functionality.
8. Do not add unnecessary dependencies.
9. Do not perform unrelated refactors.
10. Run relevant tests after changes.
11. Fix root causes rather than masking errors.
12. Update documentation when behavior changes.

---

# 20. Handling Ambiguity

Do not ask unnecessary questions about decisions already documented.

If an unresolved decision materially affects:

* security
* database design
* authorization
* API contracts
* architecture
* core user workflows

stop and ask for clarification before implementing that portion.

For small implementation details, choose the simplest approach consistent with the documented architecture.

---

# 21. Scope Discipline

Do not implement future functionality simply because it seems useful.

If a requested change falls outside V1, identify that it is outside the current scope before implementing it.

Do not expand the product silently.

---

# 22. Code Quality

Prefer:

* clear names
* small focused classes
* small focused components
* explicit types
* reusable patterns
* readable code
* predictable APIs
* maintainable architecture

Avoid unnecessary cleverness.

Readable code is preferred over compressed code.

---

# 23. Secrets & Git

Never commit:

* passwords
* API keys
* private keys
* production credentials
* sensitive `.env` files
* real patient data
* real medical records

Use fictional development/test data.

---

# 24. Documentation Synchronization

When behavior changes, update the relevant documentation.

Examples:

```text
Product behavior       → product.md / requirements.md
Permissions             → roles-and-permissions.md
Architecture            → architecture.md
Database                → database.md
API                     → api.md
Security                → security.md
UI behavior             → ui-ux.md
Localization            → internationalization.md
Development process     → development-rules.md / roadmap.md
```

Do not allow implementation and documentation to silently diverge.

---

# 25. Definition of Done

Before marking a feature complete, verify:

* Product requirements satisfied
* Correct authorization
* Clinic isolation
* Backend validation
* Correct API behavior
* Correct database behavior
* Loading state
* Empty state
* Error state
* Search/filter/sort/pagination where relevant
* English translation
* French translation
* Light mode
* Dark mode
* Relevant tests
* No security regressions
* Documentation updated where necessary

---

# 26. Final Rule

When uncertain, prioritize:

```text
Correctness
Security
Data integrity
Maintainability
Consistency
Simplicity
User experience
```

Do not sacrifice security or data integrity for convenience.

Do not sacrifice architectural consistency for speed.

Do not sacrifice product scope for unnecessary features.

Build IATROS deliberately and incrementally.
