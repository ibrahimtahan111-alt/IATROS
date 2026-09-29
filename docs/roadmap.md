# IATROS — Development Roadmap

## 1. Purpose

This roadmap defines the implementation order for IATROS.

The goal is to build IATROS incrementally while preserving the product, architecture, security, and UX decisions already defined in the project documentation.

Development must proceed from stable foundations toward user-facing features.

The application should become runnable and testable at every major phase.

The roadmap intentionally avoids implementing future features prematurely.

---

# 2. Development Principles

IATROS development follows these principles:

1. Build the foundation before feature modules.
2. Implement backend authorization before exposing sensitive frontend functionality.
3. Build reusable components before duplicating UI patterns.
4. Keep database migrations version-controlled.
5. Keep the application runnable after each milestone.
6. Test critical business rules as soon as they are introduced.
7. Do not implement deferred features simply because they are technically interesting.
8. Do not change the product model casually during implementation.
9. Prefer small, reviewable increments over large rewrites.
10. Fix architectural problems at the source rather than adding frontend workarounds.

---

# 3. Development Phases

The planned development sequence is:

```text
Phase 0  → Project foundation
Phase 1  → Database foundation
Phase 2  → Backend foundation
Phase 3  → Authentication & authorization
Phase 4  → Application shell & design system
Phase 5  → Clinic & staff management
Phase 6  → Doctor management & schedules
Phase 7  → Patient management
Phase 8  → Medical records
Phase 9  → Appointments & calendar
Phase 10 → Dashboards & KPIs
Phase 11 → Documents & auditability
Phase 12 → Internationalization & theme
Phase 13 → Security hardening
Phase 14 → Testing & quality
Phase 15 → Deployment preparation
Phase 16 → V1 release
```

The phases are ordered according to dependencies, not according to how visually impressive a feature is.

---

# 4. Phase 0 — Project Foundation

## Objective

Create the basic repository and development environment.

## Tasks

### Repository

Create:

```text
iatros/
├── frontend/
├── backend/
├── docs/
├── .gitignore
├── README.md
└── ...
```

Move the existing specification documents into:

```text
docs/
```

Recommended:

```text
docs/
├── product.md
├── requirements.md
├── roles-and-permissions.md
├── architecture.md
├── database.md
├── modules.md
├── ui-ux.md
├── api.md
├── security.md
├── internationalization.md
├── roadmap.md
└── development-rules.md
```

## Frontend

Initialize:

* React
* TypeScript
* Vite
* React Router

## Backend

Initialize:

* Java
* Spring Boot
* Maven
* Spring Web
* Spring Security
* Spring Data JPA
* Hibernate
* Bean Validation

## Database

Prepare PostgreSQL development configuration.

## Environment

Create environment configuration for:

* database connection
* backend configuration
* frontend API URL
* secrets

Secrets must not be committed to Git.

## Definition of Done

* Repository runs locally.
* Frontend starts.
* Backend starts.
* PostgreSQL connection can be configured.
* Git repository is cleanly structured.
* Documentation is present.

---

# 5. Phase 1 — Database Foundation

## Objective

Create the stable relational foundation of IATROS.

## Core entities

Implement the initial database model for:

* Clinic
* User
* Doctor
* Specialty
* Patient
* Patient-doctor association
* Medical Record
* Consultation
* Appointment
* Doctor Schedule
* Schedule Exception
* Clinic Settings
* Documents
* Audit Record
* Password Reset

## Tasks

* Create migrations.
* Create primary keys.
* Create foreign keys.
* Create unique constraints.
* Create indexes.
* Add timestamps.
* Define deletion/deactivation behavior.
* Define clinic isolation.
* Define relationships.

## Important

Database design must follow `database.md`.

Do not introduce convenience fields that duplicate authoritative relationships without a documented reason.

## Definition of Done

* Database can be created from migrations.
* Database can be recreated from zero.
* Relationships are enforced.
* Important indexes exist.
* Test data can be inserted safely.

---

# 6. Phase 2 — Backend Foundation

## Objective

Create the backend architecture before implementing feature logic.

## Tasks

Implement:

* application configuration
* database connection
* entity/domain model
* repositories
* services
* controllers
* DTOs
* validation
* exception handling
* API response conventions
* API error codes
* logging
* transaction boundaries

## Architecture

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

Controllers must not contain business logic that belongs in services.

JPA entities must not be exposed directly as public API contracts.

---

# 7. Phase 3 — Authentication & Authorization

## Objective

Establish secure access control before implementing sensitive clinic functionality.

## Implement

* Login
* Logout/session termination
* Password hashing
* Authentication
* Forgot password
* Password reset
* User status/deactivation
* Role authorization
* Clinic isolation

## Roles

Implement:

```text
OWNER
DOCTOR
SECRETARY
```

Patient authentication remains deferred.

## Authorization

Implement backend authorization for:

* clinic
* staff
* doctors
* patients
* medical records
* appointments
* schedules
* dashboards
* KPIs

## Critical rule

The frontend must never be the final authority for permissions.

Every sensitive API request must be authorized by the backend.

## Definition of Done

A test can demonstrate that:

* Owner can access owner functionality.
* Doctor cannot access another doctor's restricted data.
* Secretary cannot access medical records.
* A user cannot access another clinic's data.
* Unauthorized API requests are rejected.

---

# 8. Phase 4 — Application Shell & Design System

## Objective

Build the reusable frontend foundation before building individual screens.

## Implement

### Layout

* Sidebar
* Top navigation
* Clinic name
* User menu
* Language selector
* Theme selector
* Main content area

### Components

Create reusable components for:

* Buttons
* Inputs
* Selects
* Date inputs
* Modals
* Dialogs
* Cards
* Tables
* Pagination
* Search
* Filters
* Sorting
* Tabs
* Badges
* Alerts
* Toasts
* Loading states
* Empty states
* Error states
* Confirmation dialogs

## Routing

Create route structure and route protection.

Example:

```text
/login
/forgot-password
/reset-password

/dashboard
/calendar
/patients
/patients/:id
/doctors
/doctors/:id
/activity
/settings
```

## Definition of Done

The application has a consistent shell and reusable component system.

No major feature should need to create its own version of standard UI components.

---

# 9. Phase 5 — Clinic & Staff Management

## Objective

Allow the clinic owner to establish and manage the clinic.

## Clinic

Implement:

* Clinic profile
* Clinic name
* Contact information
* Address
* Time zone
* Basic clinic settings

## Staff

Implement:

* Add team member
* Assign role
* Edit staff member
* Deactivate staff member
* View staff
* Search
* Filter
* Sort
* Pagination

## Invitation/account flow

Implement the necessary account activation flow for newly added staff.

## Permissions

Owner:

* Full staff management

Doctor:

* Own profile only

Secretary:

* No staff management

## Definition of Done

An owner can create a usable clinic team without manually modifying the database.

---

# 10. Phase 6 — Doctor Management & Schedules

## Objective

Establish doctor profiles and appointment availability.

## Doctor profile

Implement:

* Name
* Contact information
* Professional information
* Primary specialty
* Additional specialty text
* Status
* Profile information

## Specialty

Implement a controlled specialty model while keeping the additional specialty field flexible.

## Schedule

Implement:

* Weekly schedule
* Multiple working periods per day
* Breaks
* Availability
* Schedule editing
* Schedule exceptions

## Business rules

The backend must prevent appointments from being created outside valid availability unless an explicitly supported override exists.

Conflicting appointments must be prevented.

## Definition of Done

Each active doctor has a valid schedule that can be used by the appointment system.

---

# 11. Phase 7 — Patient Management

## Objective

Create the clinic's central patient-management workflow.

## Implement

* Patient list
* Search
* Filtering
* Sorting
* Pagination
* Add patient
* Edit patient
* Patient profile
* Primary doctor
* Authorized doctors
* Patient status
* Patient contact information

## Access

Owner:

* Clinic-wide patient access

Doctor:

* Authorized patients only

Secretary:

* Booking-related patient information only

## Important

Patient records must remain clinic-owned.

Changing the primary doctor must not delete the patient's medical history.

## Definition of Done

A patient can be created, found, updated, assigned to doctors, and safely accessed according to permissions.

---

# 12. Phase 8 — Medical Records

## Objective

Implement the generic medical-record foundation.

## Implement

### Patient medical record

* Allergies
* Chronic conditions
* Current medications
* Past medical history
* General medical notes
* Consultation history
* Diagnoses
* Clinical/treatment notes
* Follow-up information
* Medical documents

## Consultation

Implement:

* Consultation date/time
* Doctor
* Reason
* Notes
* Diagnosis
* Treatment/plan
* Follow-up
* Consultation history

## Authorization

Only authorized doctors and permitted owner-level users can access clinical information.

Secretary must not have unrestricted medical-record access.

## Future extensibility

Do not create specialty-specific patient tables.

Future modules should extend the generic medical-record architecture.

## Definition of Done

A doctor can conduct and review a patient's basic clinical history securely.

---

# 13. Phase 9 — Appointments & Calendar

## Objective

Build the primary scheduling workflow.

## Calendar

Implement:

* Day view
* Week view
* Month view
* Doctor filter
* Date navigation
* Appointment status
* Appointment details

## Appointment creation

Implement:

* Patient selection
* Doctor selection
* Date
* Time
* Duration
* Appointment type
* Reason/notes
* Status

Default duration:

```text
30 minutes
```

## Appointment lifecycle

Support:

```text
Scheduled
Confirmed
Waiting
In consultation
Completed
Cancelled
No-show
```

## Actions

* Create
* Edit
* Reschedule
* Cancel
* Change status
* View details

## Integrity

Backend must validate:

* Doctor availability
* Schedule exceptions
* Conflicting appointments
* Valid patient access
* Valid doctor access
* Clinic ownership

Cancelled appointments must remain historically visible.

## Definition of Done

A clinic can manage its appointment workflow entirely through the calendar.

---

# 14. Phase 10 — Dashboards & KPIs

## Objective

Expose useful operational information while respecting authorization.

## Owner dashboard

Implement:

* Clinic activity
* Appointment activity
* Patient activity
* Doctor activity
* Clinic KPIs
* Relevant trends

## Doctor dashboard

Implement:

* Patients today
* Appointments today
* Own patient list
* Relevant condition information
* Appointment information
* Own KPIs

## Restrictions

Doctors must not receive unrestricted clinic-wide KPI data.

Secretaries do not receive owner/doctor dashboards unless explicitly permitted later.

## Important

Dashboard endpoints must use the same authorization rules as underlying resources.

Do not implement dashboards by fetching unrestricted data and hiding it in the frontend.

---

# 15. Phase 11 — Documents & Auditability

## Objective

Complete the supporting infrastructure for sensitive clinic data.

## Documents

Implement:

* Upload
* Metadata
* File type validation
* File size validation
* Secure retrieval
* Association with patient/medical record
* Delete/archive behavior

Actual files must be stored through a storage abstraction rather than tightly coupling the application to one provider.

## Audit records

Track important sensitive operations such as:

* Medical-record changes
* Patient-data changes
* Staff changes
* Permission-sensitive actions
* Important appointment changes
* Document operations

Audit records must not expose unnecessary medical information.

## Definition of Done

Sensitive operations have an appropriate audit trail.

---

# 16. Phase 12 — Internationalization & Theme

## Objective

Complete the V1 presentation layer.

## Language

Implement:

* English
* French
* Language selector
* Persistent preference
* Localized dates
* Localized times
* Localized numbers
* Localized validation
* Localized errors
* Localized notifications

Follow `internationalization.md`.

## Theme

Implement:

* Light mode
* Dark mode
* Persistent theme preference
* Accessible contrast
* Consistent component behavior

## Definition of Done

The complete V1 interface works in English and French and supports light/dark mode.

---

# 17. Phase 13 — Security Hardening

## Objective

Perform a dedicated security pass after all major functionality exists.

## Review

Verify:

* Authentication
* Authorization
* Clinic isolation
* Patient access
* Medical-record access
* Document access
* Input validation
* SQL/ORM safety
* CORS
* CSRF strategy
* Rate limiting
* Session/token security
* Password reset security
* File-upload security
* Sensitive logging
* Enumeration resistance
* Secret management
* Dependency vulnerabilities

## Abuse testing

Test attempts to:

* access another clinic
* access another doctor's patients
* access another doctor's appointments
* access medical records as secretary
* modify unauthorized records
* retrieve unauthorized documents
* manipulate IDs
* bypass frontend restrictions
* submit invalid data

---

# 18. Phase 14 — Testing & Quality

## Objective

Verify the application as a complete system.

Testing should occur throughout development, but this phase performs the final comprehensive pass.

## Backend tests

Include:

* Unit tests
* Service tests
* Repository tests where valuable
* Controller/API tests
* Authorization tests
* Validation tests
* Business-rule tests

## Critical scenarios

Test:

### Authentication

* valid login
* invalid login
* password reset
* deactivated account

### Authorization

* owner access
* doctor access
* secretary access
* cross-clinic access
* patient-doctor authorization

### Appointments

* valid booking
* conflicting booking
* unavailable doctor
* cancelled appointment
* rescheduling
* status transitions

### Medical records

* authorized access
* unauthorized access
* record creation
* record modification
* consultation history

### Data

* pagination
* filtering
* sorting
* search
* clinic isolation

## Frontend tests

Test:

* navigation
* forms
* validation
* role-based UI
* calendar interactions
* search/filter/sort
* language switching
* theme switching
* loading/error/empty states

## End-to-end tests

Cover the most important real workflows.

---

# 19. Phase 15 — Deployment Preparation

## Objective

Prepare IATROS to run outside the development environment.

## Tasks

Prepare:

* Production configuration
* Environment variables
* Database migration process
* Secure secret management
* HTTPS
* CORS configuration
* Logging
* Error monitoring
* Database backups
* File storage
* Health checks
* Build process

## Production principles

Production configuration must never depend on local development secrets.

The production database must not be manually modified as part of normal deployment.

Database changes must use migrations.

---

# 20. Phase 16 — V1 Release

## Objective

Release the defined IATROS V1 without expanding scope.

## V1 must include

### Authentication

* Login
* Password reset
* Account management

### Clinic

* Clinic settings

### Staff

* Staff management
* Roles

### Doctors

* Doctor profiles
* Specialties
* Schedules

### Patients

* Patient management
* Patient-doctor authorization

### Medical

* Medical records
* Consultations
* Basic clinical history
* Documents

### Appointments

* Calendar
* Scheduling
* Availability
* Appointment lifecycle

### Dashboards

* Owner dashboard
* Doctor dashboard
* KPIs

### UX

* Search
* Filtering
* Sorting
* Pagination
* Loading states
* Empty states
* Error states
* Confirmation dialogs

### Localization

* English
* French

### Theme

* Light
* Dark

### Security

* Backend authorization
* Clinic isolation
* Sensitive-data protection
* Auditability

---

# 21. Features Explicitly Deferred

The following must not enter V1 unless the product definition is deliberately changed:

* Patient portal
* Patient accounts
* Online patient booking
* Mobile application
* Google Calendar integration
* Outlook integration
* Online payments
* Subscription billing implementation
* Advanced insurance management
* Advanced laboratory management
* Advanced imaging management
* Advanced prescription management
* Specialty-specific clinical modules
* AI clinical features
* Multi-clinic management

These features may be planned later but should not silently enter the V1 implementation.

---

# 22. Recommended Implementation Order Within a Feature

Every feature should generally follow this sequence:

```text
1. Requirements
2. Database changes
3. Migration
4. Domain/entity model
5. Repository
6. Service/business logic
7. Authorization
8. DTOs
9. API/controller
10. Backend tests
11. Frontend route/page
12. Reusable UI components
13. API integration
14. Loading/empty/error states
15. Search/filter/sort/pagination
16. Localization
17. Accessibility
18. Frontend tests
19. End-to-end workflow
20. Documentation
```

Not every feature requires every step, but developers should deliberately consider each one.

---

# 23. Vertical Slice Principle

Where practical, implement features as complete vertical slices.

For example, patient creation should not stop after creating the database table.

A complete slice should include:

```text
Database
↓
Backend
↓
Authorization
↓
API
↓
Frontend
↓
Validation
↓
Feedback
↓
Tests
```

This makes the application usable throughout development and exposes architectural problems early.

---

# 24. Milestone Strategy

Each major milestone should leave IATROS in a runnable state.

Suggested milestones:

### Milestone 1 — Foundation

* Project setup
* Database
* Backend foundation
* Frontend foundation

### Milestone 2 — Secure Clinic

* Authentication
* Roles
* Authorization
* Clinic
* Staff

### Milestone 3 — Doctors & Patients

* Doctors
* Specialties
* Schedules
* Patients
* Patient authorization

### Milestone 4 — Clinical Core

* Medical records
* Consultations
* Documents

### Milestone 5 — Scheduling

* Calendar
* Appointments
* Availability
* Appointment lifecycle

### Milestone 6 — Operations

* Dashboards
* KPIs
* Clinic activity

### Milestone 7 — Product Polish

* English
* French
* Light/dark mode
* UX refinement
* Accessibility
* Error handling

### Milestone 8 — Release

* Security hardening
* Testing
* Deployment
* Documentation
* V1 release

---

# 25. Git Strategy

Development should use meaningful commits.

Examples:

```text
feat(auth): add login endpoint
feat(patients): add patient creation
feat(calendar): add weekly calendar
feat(medical-records): add consultation history
fix(appointments): prevent overlapping bookings
test(auth): add authorization tests
refactor(patients): extract patient authorization service
```

Avoid commits such as:

```text
update
changes
stuff
final
final-final
```

Commit messages should communicate the actual change.

---

# 26. Pull Request / Change Review

Before accepting a significant change, verify:

* Does it match `product.md`?
* Does it satisfy `requirements.md`?
* Does it respect `roles-and-permissions.md`?
* Does it follow `architecture.md`?
* Does it follow `security.md`?
* Does it preserve database integrity?
* Does it respect the UI/UX specification?
* Does it support English and French?
* Does it introduce unnecessary scope?
* Does it duplicate existing functionality?
* Are tests included where appropriate?

---

# 27. Change Management

The roadmap is not permission to modify the product casually.

If implementation reveals a problem with an existing decision:

1. Identify the problem.
2. Determine which source-of-truth document is affected.
3. Propose the change.
4. Update the relevant documentation.
5. Check dependencies.
6. Update affected implementation plans.
7. Only then implement the change.

Documentation and implementation must not silently diverge.

---

# 28. What Should Be Built First

The first actual coding target should be:

```text
Repository
↓
Frontend + Backend setup
↓
PostgreSQL
↓
Database migrations
↓
Core domain model
↓
Authentication
↓
Authorization
↓
Application shell
```

Do not begin by building the calendar or dashboard simply because those are visually prominent.

The calendar depends on:

* doctors
* patients
* schedules
* authorization
* appointments
* clinic isolation

Building it first would create unnecessary rework.

---

# 29. What Should Not Be Optimized Too Early

Do not prematurely optimize:

* advanced caching
* distributed architecture
* microservices
* event-driven architecture
* complex state management
* advanced analytics infrastructure
* AI infrastructure
* multi-region deployment
* elaborate notification infrastructure

IATROS V1 should use a straightforward architecture that can scale without introducing unnecessary complexity.

---

# 30. Final Roadmap Principle

**Build IATROS in dependency order, not feature-hype order.**

The system should first become:

```text
Secure
↓
Structurally sound
↓
Clinically coherent
↓
Operationally useful
↓
Polished
↓
Tested
↓
Deployable
```

Every phase should strengthen the previous one rather than forcing the application to be repeatedly rebuilt.

The final objective is not simply to produce many screens.

The objective is to produce a **secure, maintainable, coherent clinic-management SaaS whose architecture can support future specialty modules and additional capabilities without rewriting its core.**
