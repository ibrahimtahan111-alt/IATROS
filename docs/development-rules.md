# IATROS — Development Rules

## 1. Purpose

This document defines the rules that must be followed when developing IATROS.

These rules apply to:

* Human developers
* Cursor
* Codex
* AI coding assistants
* Future contributors

The purpose is to ensure that implementation remains consistent with:

* `product.md`
* `requirements.md`
* `roles-and-permissions.md`
* `architecture.md`
* `database.md`
* `modules.md`
* `ui-ux.md`
* `design-system.md`
* `decisions.md`
* `api.md`
* `security.md`
* `internationalization.md`
* `roadmap.md`

These documents together form the product and technical source of truth.

---

# 2. Source of Truth

Before implementing a feature, developers and AI coding assistants must consult the relevant documentation.

Priority order:

```text
1. Security requirements
2. Product definition
3. Requirements
4. Roles & permissions
5. Architecture
6. Database design
7. API specification
8. UI/UX specification
9. Internationalization
10. Roadmap
11. Existing implementation
```

Existing code must not automatically override documented requirements.

If existing code contradicts the documentation, the contradiction must be identified before extending the code.

---

# 3. Do Not Guess Important Product Decisions

AI coding assistants must not invent major product behavior.

If a decision is genuinely unresolved and materially affects:

* data model
* security
* authorization
* architecture
* user workflow
* API contract
* database structure

the assistant must stop and ask for clarification.

However, it should **not ask about decisions that have already been established in the documentation**.

Examples already defined:

* IATROS targets solo doctors and small clinics.
* Owner has full access.
* Doctor access is restricted.
* Secretary handles booking/calendar operations.
* Patients are clinic-owned.
* Patients have medical records.
* Doctors have primary and additional specialties.
* Calendar is the primary appointment interface.
* English and French are supported.
* PostgreSQL is used.
* React + TypeScript + Vite is used.
* Spring Boot is used.
* Future specialty modules extend the generic clinical model.

---

# 4. Avoid Unnecessary Scope

Do not implement features simply because they could be useful.

Before adding functionality, determine:

```text
Is it required by V1?
Is it required by an existing workflow?
Is it required for security?
Is it required for architectural integrity?
```

If the answer is no, defer it.

Do not silently add:

* patient portal
* patient login
* mobile app
* online payments
* billing
* Google Calendar
* Outlook integration
* AI clinical features
* advanced EMR
* specialty-specific modules
* multi-clinic support

unless the product definition is deliberately changed.

---

# 5. No Premature Overengineering

IATROS should be modular and extensible, but not unnecessarily complex.

Do not introduce:

* microservices
* event-driven infrastructure
* unnecessary message queues
* distributed systems
* excessive abstraction layers
* complex caching
* unnecessary design patterns
* multiple databases
* unnecessary external services

unless a documented requirement justifies them.

Prefer a well-structured modular monolith for V1.

---

# 6. Technology Rules

## Frontend

Use:

* React
* TypeScript
* Vite
* React Router

Do not introduce another frontend framework without an explicit architectural decision.

## Backend

Use:

* Java
* Spring Boot
* Spring Web
* Spring Security
* Spring Data JPA
* Hibernate
* Bean Validation
* Maven

## Database

Use:

* PostgreSQL

Database changes must use migrations.

---

# 7. TypeScript Rules

TypeScript must be used properly.

Avoid:

```text id="4k3w5c"
any
```

unless there is a documented technical reason.

Prefer explicit types and interfaces.

API responses should have typed models.

Do not duplicate the same type definition unnecessarily across multiple components.

Shared domain types should have a clear ownership location.

---

# 8. React Rules

Components should have clear responsibilities.

Avoid extremely large components.

If a component contains unrelated responsibilities, split it.

Prefer:

```text id="j5z6py"
Page
 ├── Header
 ├── Filters
 ├── Table
 └── Modal
```

rather than placing the entire page in one component.

Reusable UI behavior belongs in reusable components or hooks.

Business rules should not be buried inside visual components.

---

# 9. Frontend Business Logic

The frontend may provide:

* user experience
* client-side validation
* optimistic presentation where safe
* filtering
* formatting
* navigation
* UI state

The frontend must **not** be the authoritative source for:

* permissions
* patient authorization
* clinic isolation
* appointment conflicts
* doctor availability
* medical-record authorization
* business-critical validation

The backend must enforce those rules.

---

# 10. Backend Layering

Backend code should follow:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

## Controllers

Controllers should:

* receive requests
* validate request structure
* invoke services
* return appropriate responses

Controllers should not contain large business workflows.

## Services

Services contain:

* business rules
* authorization decisions where appropriate
* transactions
* workflow logic

## Repositories

Repositories handle data persistence.

Repositories should not become a substitute for business services.

---

# 11. DTO Rules

Do not expose JPA entities directly through REST endpoints.

Use DTOs.

Example:

```text id="q7r5k6"
PatientEntity
        ↓
PatientResponse
```

Request DTOs and response DTOs should be separated where their responsibilities differ.

API contracts should remain stable even if internal database entities change.

---

# 12. Database Rules

Every schema change must use a migration.

Never manually modify production schema as part of normal development.

Each migration should be:

* versioned
* reproducible
* reviewable
* tested

Do not store translated UI strings as database values.

Use stable internal identifiers.

Example:

```text id="52s1cf"
COMPLETED
```

not:

```text id="3r7fyt"
Terminé
```

---

# 13. Entity Relationship Rules

Respect the defined domain model.

Core relationship:

```text id="l6qeqp"
Clinic
  ↓
Users / Staff / Doctors
  ↓
Patients
  ↓
Medical Records
  ↓
Consultations

Doctors
  ↓
Schedules
  ↓
Appointments
```

Patients are clinic-owned.

Patients can have:

* one primary doctor
* additional authorized doctors

Changing a doctor relationship must not accidentally delete patient medical history.

---

# 14. Medical Data Rules

Medical information is sensitive.

Medical-record functionality must prioritize:

* authorization
* confidentiality
* data integrity
* auditability
* minimal exposure

Do not return medical information in endpoints that do not require it.

Do not place sensitive medical information into:

* URLs
* frontend logs
* debug logs
* analytics events
* error messages

unless explicitly required and appropriately protected.

---

# 15. Authorization Rules

Authorization is deny-by-default.

Every protected endpoint must have a clear authorization rule.

Always ask:

```text
Who is making the request?
Which clinic do they belong to?
What role do they have?
What resource are they accessing?
Are they authorized to access this specific resource?
```

Do not rely only on:

```text
if role == DOCTOR
```

A doctor being a doctor does not automatically mean they can access every patient.

Patient-doctor authorization must be respected.

---

# 16. Clinic Isolation

Clinic isolation is mandatory.

Every clinic-scoped query must ensure that data belongs to the authenticated user's clinic.

Never trust a client-provided:

```text
clinicId
```

as proof of authorization.

The server must derive the user's clinic from the authenticated context and enforce the boundary.

Cross-clinic access must fail even if the requester manipulates:

* IDs
* URLs
* query parameters
* request bodies
* filters

---

# 17. Doctor Access Rules

Doctors must only access data permitted by their role and relationships.

A doctor must not automatically receive:

* other doctors' private information
* unrestricted clinic staff information
* owner settings
* clinic-wide administrative KPIs
* unauthorized patient records
* unauthorized medical records
* unauthorized documents

The backend must enforce this independently of the frontend.

---

# 18. Secretary Access Rules

The secretary role is intentionally limited.

Secretary functionality focuses on:

* patient booking
* appointment management
* calendar operations
* necessary patient information for scheduling

A secretary must not receive unrestricted medical-record access.

Do not implement hidden medical information in the frontend and assume it is secure.

If the API returns it, it should be considered exposed.

---

# 19. Owner Access Rules

The owner has full clinic management access within V1.

Owner access still requires:

* authentication
* clinic isolation
* validation
* auditability for sensitive actions

"Owner" does not mean bypassing application integrity rules.

---

# 20. Appointment Rules

Appointments must always respect:

* clinic ownership
* doctor ownership
* patient authorization
* doctor schedule
* schedule exceptions
* existing appointments
* appointment status
* valid date/time

The backend must prevent overlapping appointments unless the business rules explicitly allow them.

Never rely only on frontend calendar validation.

---

# 21. Appointment Status Rules

Use stable enum values.

```text id="c9p5zn"
SCHEDULED
CONFIRMED
WAITING
IN_CONSULTATION
COMPLETED
CANCELLED
NO_SHOW
```

The UI translates these values.

Do not duplicate appointment-status logic throughout the application.

Centralize status rules.

---

# 22. Doctor Schedule Rules

Schedules must represent actual availability.

Support:

* multiple working periods per day
* breaks
* exceptions
* unavailable periods

Availability calculations must be implemented in backend business logic.

The frontend may display availability but must not define authoritative availability.

---

# 23. Search, Filter, Sort & Pagination

Search, filtering, sorting, and pagination are global application requirements.

Every relevant large dataset should support them.

Examples:

* Patients
* Doctors
* Staff
* Appointments where applicable
* Consultations where applicable
* Documents
* Activity records

Rules:

1. Operate only on authorized data.
2. Use server-side pagination for large datasets.
3. Do not load unlimited records simply to filter them in the browser.
4. Use database indexes where appropriate.
5. Prevent arbitrary expensive queries.
6. Preserve filters when navigating where useful.
7. Provide clear reset functionality.

---

# 24. Table Rules

Tables should consistently support:

* column headings
* sorting where relevant
* pagination
* loading state
* empty state
* error state
* row actions
* responsive behavior

Do not create a unique table implementation for every page if a shared table system can satisfy the requirement.

---

# 25. Form Rules

Every important form must have:

* clear labels
* validation
* required/optional indication
* useful error messages
* loading state during submission
* disabled state where necessary
* success feedback
* error feedback
* cancellation behavior

Forms must not silently discard user input after recoverable errors.

---

# 26. Destructive Actions

Actions such as:

* deactivation
* deletion
* cancellation
* irreversible changes

must require appropriate confirmation.

The confirmation should explain:

* what will happen
* what data is affected
* whether the action can be reversed

Prefer deactivation/archive where appropriate over destructive deletion.

---

# 27. Loading, Empty & Error States

Every data-dependent screen must account for:

### Loading

The user should know data is being loaded.

### Empty

The user should understand:

* there is currently no data
* what they can do next

### Error

The user should receive:

* understandable feedback
* a retry option where appropriate

Do not leave blank screens.

---

# 28. API Rules

All APIs should follow the documented API conventions.

Use:

```text id="6qcn5o"
/api/v1/...
```

API responses must be predictable.

Use appropriate HTTP status codes.

Use machine-readable error codes.

Do not expose stack traces or internal implementation details.

---

# 29. API Validation

Validate input on the backend even if the frontend validates it.

Never trust:

* request body
* query parameters
* route parameters
* uploaded files
* client-side validation

Backend validation is mandatory.

---

# 30. Transaction Rules

Use transactions for operations that must succeed or fail as a unit.

Examples:

* creating related records
* modifying patient-doctor authorization
* changing appointment relationships
* important medical-record operations
* staff/account changes

Avoid leaving partially completed business operations.

---

# 31. Concurrency

Consider concurrency for operations where two users could modify the same resource simultaneously.

Particularly important:

* appointment creation
* appointment rescheduling
* schedule modification
* staff status changes
* medical-record updates

The database/backend must provide the final integrity guarantee.

---

# 32. Internationalization Rules

All user-facing text must be translated.

Never hardcode English UI text directly into components.

Every V1 feature must support:

* English
* French

Do not use translated text as internal identifiers.

Follow `internationalization.md`.

---

# 33. Theme Rules

All UI components must support:

* light mode
* dark mode

Do not create components that only look correct in one theme.

Avoid hardcoded colors that bypass the application's design system.

---

# 34. Accessibility Rules

UI must consider:

* keyboard navigation
* focus management
* semantic HTML
* accessible labels
* dialog accessibility
* sufficient contrast
* form error association
* screen-reader compatibility

Accessibility should be considered while building components, not added only at the end.

---

# 35. File Upload Rules

Uploaded documents must be treated as untrusted input.

Validate:

* file size
* file type
* extension
* content where appropriate
* authorization

Do not expose raw storage paths.

File access must go through authorized backend endpoints.

---

# 36. Logging Rules

Logs must be useful without exposing sensitive information.

Do not log:

* passwords
* authentication tokens
* medical notes
* unnecessary patient details
* sensitive documents
* secrets

Prefer IDs and safe metadata where appropriate.

---

# 37. Error Handling

Errors must be:

* predictable
* safe
* actionable
* localized at the presentation layer

Backend errors should provide appropriate machine-readable identifiers.

Frontend should translate those identifiers.

Unexpected internal errors must not reveal implementation details.

---

# 38. Configuration Rules

Configuration must be separated from source code.

Use environment variables or appropriate configuration mechanisms for:

* database credentials
* secrets
* API keys
* storage configuration
* external services
* production-specific settings

Never commit secrets to GitHub.

---

# 39. Dependency Rules

Before adding a dependency, consider:

* Is it actually needed?
* Does the existing stack already solve the problem?
* Is it maintained?
* Does it introduce unnecessary complexity?
* Does it have a reasonable security posture?
* Does it significantly increase bundle or build size?

Do not add dependencies merely for convenience.

---

# 40. Reusable Component Rules

Create reusable components when:

* the same pattern appears multiple times
* behavior must remain consistent
* accessibility is important
* the component represents a clear design-system element

Do not create abstractions purely to avoid a few repeated lines.

The goal is reusable clarity, not maximum abstraction.

---

# 41. Naming Rules

Use clear, descriptive names.

Examples:

```text id="4v0i5v"
PatientService
AppointmentService
DoctorScheduleService
PatientAuthorizationService
MedicalRecordController
AppointmentRepository
```

Avoid vague names such as:

```text id="0v7f6j"
Helper
Manager
Utils
Thing
DataHandler
CommonService
```

unless the responsibility is genuinely clear from the name.

---

# 42. File Organization

Organize code by domain/module where practical.

Backend example:

```text id="c1k7i8"
auth/
clinic/
staff/
doctor/
patient/
medicalrecord/
consultation/
appointment/
schedule/
dashboard/
document/
audit/
```

Frontend should similarly avoid placing the entire application into generic folders such as:

```text id="g7g1dv"
components/
services/
utils/
```

with hundreds of unrelated files.

Shared components belong in shared areas; domain-specific code belongs with its domain.

---

# 43. Avoid God Classes

Do not create:

* one giant service
* one giant controller
* one giant React component
* one giant utility file
* one giant API client

Each module should have clear responsibility.

---

# 44. Avoid Duplicate Business Logic

If a rule exists in multiple locations, determine whether it belongs in a shared domain/service layer.

Examples:

* patient authorization
* appointment conflict detection
* doctor availability
* appointment status transitions

These rules should have one authoritative implementation.

---

# 45. API Client Rules

Frontend API calls should use a consistent API layer.

Do not scatter raw HTTP requests throughout components.

Prefer:

```text id="i5kg4x"
Component
   ↓
Hook / Query
   ↓
API Service
   ↓
Backend
```

This keeps network behavior consistent.

---

# 46. State Management

Use local component state when state is local.

Use shared state only when multiple parts of the application genuinely require it.

Do not place all application data into a single global store simply because a state-management library exists.

Server state should be treated differently from local UI state.

Server state uses TanStack Query; local UI state uses React state. A global client store is not introduced unless a concrete need arises (`decisions.md` D-040).

---

# 47. Caching

Caching must never bypass authorization.

Do not cache sensitive data in a way that could allow one user or clinic to receive another user's data.

Cache invalidation must be considered whenever mutable data is cached.

For V1, prefer correctness over aggressive caching.

---

# 48. Testing Rules

New business functionality should include appropriate tests.

At minimum, critical backend rules must be tested.

Particularly:

* authorization
* clinic isolation
* patient authorization
* appointment conflicts
* schedules
* medical-record access
* validation

A feature is not complete merely because the UI works manually.

**Test layers (Phase 14):**

| Layer | Tool | Location | Covers |
| --- | --- | --- | --- |
| Backend API and rules | JUnit, MockMvc, PostgreSQL `iatros_test` | `backend/src/test` | Business rules, validation, authorization, clinic isolation, the abuse matrix (`security.md` §15) |
| Frontend components and pages | Vitest, Testing Library, `fakeApi` | `frontend/src/**/*.test.tsx` | Forms, validation, role-based UI, states, both languages, theme |
| End-to-end | Playwright (Chromium) | `frontend/e2e/*.e2e.ts` | A few real cross-role workflows against the running backend and demo clinic |

* Backend coverage is measured with JaCoCo (`./mvnw verify`). The Phase 14 baseline was 91% of lines and 80% of branches. Treat a coverage drop in a business-rule package (`access`, `appointment`, `medicalrecord`, `patient`, `schedule`, `document`, `security`) as a review question, not a number to game. The dev seeder and the SMTP adapter are intentionally untested.
* End-to-end tests stay few and focused on workflows that only work when the pieces are joined (sign-in, a secretary booking for a doctor, role restrictions in the real UI, preferences surviving a reload). Detailed cases belong in the faster layers.
* End-to-end tests must leave the demo clinic as they found it: they use unique names, clean up through the API (for example, archive the patient they created), and restore account preferences they change.
* When running a single backend test class that has `@Nested` classes, use a pattern (`-Dtest=MedicalRecordApiTests*`); without it, Surefire runs none of the nested tests.

---

# 49. Security Testing

For every protected feature, test negative cases.

Examples:

```text id="r4zq7d"
Doctor A → Doctor B's patient
Doctor → unauthorized medical record
Secretary → medical record
Clinic A → Clinic B patient
User → modified resource ID
Unauthenticated user → protected endpoint
```

Expected result:

```text id="x2d8ck"
Access denied.
```

Do not only test successful workflows.

---

# 50. Git Rules

Never commit:

* passwords
* API keys
* private certificates
* production secrets
* `.env` files containing secrets
* database dumps containing sensitive data
* real patient data

Use safe development fixtures.

---

# 51. Test Data Rules

Development data must be fictional.

Never use real patient medical information in:

* GitHub
* test databases
* screenshots
* demos
* automated tests
* public documentation

---

# 52. Documentation Rules

When implementation changes behavior, update the appropriate documentation.

Examples:

Database change:

```text id="v8m3kz"
database.md
```

API change:

```text id="n9jv4w"
api.md
```

Security change:

```text id="u3w7pk"
security.md
```

Product behavior change:

```text id="x8s4qr"
product.md
requirements.md
```

Do not let documentation become outdated.

---

# 53. AI Coding Assistant Rules

When Cursor/Codex is used:

1. Read the relevant documentation before coding.
2. Understand the existing architecture before modifying it.
3. Inspect related code before creating new code.
4. Reuse existing patterns when appropriate.
5. Do not create duplicate implementations.
6. Do not silently change business requirements.
7. Do not weaken authorization to make a feature work.
8. Do not bypass backend validation.
9. Do not remove tests to make builds pass.
10. Do not introduce dependencies without justification.
11. Keep changes focused.
12. Explain important architectural decisions when requested.
13. Run relevant tests after changes.
14. Fix the root cause rather than masking failures.
15. Update documentation when behavior changes.

---

# 54. AI Must Not "Fix" Security by Hiding UI

If a user should not have access to something, the solution is not:

```text id="pj9m2f"
Hide the button.
```

The solution is:

```text id="m5j1ro"
Backend authorization
+
Frontend visibility
```

The frontend should provide the correct UX, but the backend must enforce the rule.

---

# 55. AI Must Not Modify the Database Casually

An AI coding assistant must not:

* delete tables to fix development errors
* drop data without explicit reason
* bypass migrations
* change relationships without reviewing dependencies
* rename fields without checking API/frontend impact

Database changes must be deliberate and migration-based.

---

# 56. AI Must Not Rewrite Working Systems Unnecessarily

If existing code works and satisfies the architecture, extend it.

Do not rewrite a module simply because another implementation looks cleaner unless there is a concrete reason.

Avoid large unrelated refactors during feature development.

---

# 57. Feature Completion Checklist

Before considering a feature complete, verify:

### Product

* Does it match product requirements?
* Is it within V1 scope?

### Backend

* Domain model correct?
* Business logic correct?
* Validation implemented?
* Authorization implemented?
* Clinic isolation enforced?
* DTOs used?
* Errors handled?

### Database

* Migration created?
* Constraints correct?
* Indexes considered?
* Data integrity preserved?

### Frontend

* Correct route?
* Correct role visibility?
* Loading state?
* Empty state?
* Error state?
* Validation?
* Search/filter/sort/pagination where relevant?
* Responsive behavior?

### i18n

* English?
* French?
* No hardcoded strings?

### Security

* Unauthorized access tested?
* Sensitive information protected?
* Logs safe?

### Testing

* Backend tests?
* Frontend tests where appropriate?
* Critical workflow tested?

### Documentation

* Relevant documentation updated?

---

# 58. Change Checklist

Before making a significant change:

```text
1. Identify affected module.
2. Identify affected documentation.
3. Identify database impact.
4. Identify API impact.
5. Identify authorization impact.
6. Identify UI/UX impact.
7. Identify internationalization impact.
8. Identify testing requirements.
9. Implement.
10. Test.
11. Update documentation.
```

---

# 59. Definition of Done

A feature is considered done only when:

* It works functionally.
* It respects the product definition.
* It respects role permissions.
* Backend authorization is enforced.
* Clinic isolation is enforced.
* Validation exists.
* Error handling exists.
* Loading/empty/error states exist.
* Relevant search/filter/sort/pagination exists.
* English and French are supported.
* Light/dark mode works.
* Tests cover important behavior.
* No secrets or sensitive test data are introduced.
* Documentation is updated where necessary.
* The code follows the established architecture.

---

# 60. Final Development Principle

**IATROS should be built deliberately, not merely made to work.**

Every implementation decision should answer three questions:

```text
Does it satisfy the product?
Does it preserve the architecture?
Does it remain secure and maintainable?
```

If the answer to any of these is no, the implementation should be reconsidered.

The goal is not to generate the largest amount of code.

The goal is to build a **coherent, secure, maintainable clinic-management platform whose foundation can support future growth without unnecessary rewrites.**
