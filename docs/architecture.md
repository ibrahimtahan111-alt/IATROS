# IATROS — Architecture

## 1. Purpose

This document defines the technical architecture of IATROS V1.

It establishes the technology stack, application structure, communication patterns, security boundaries, and architectural principles that implementation must follow.

The architecture must support the current V1 requirements while allowing IATROS to expand into additional clinic, specialty, patient-facing, and AI functionality in the future.

---

# 2. Technology Stack

The following technologies are the approved V1 stack.

## Frontend

* **React**
* **TypeScript**
* **Vite**
* **React Router**
* **TanStack Query** for server state
* **i18next + react-i18next** for internationalization
* **CSS Modules + design tokens**, with **Radix UI primitives** for accessible behavior (see `design-system.md`)
* **React Hook Form + Zod** for forms

The full list and rationale are recorded in `decisions.md` (D-040).

The frontend must be written in TypeScript.

JavaScript should not be introduced for new frontend application code.

---

## Backend

* **Java**
* **Spring Boot**
* **Spring Web**
* **Spring Security**
* **Spring Data JPA**
* **Hibernate**
* **Bean Validation**

The backend must expose a RESTful API consumed by the frontend.

---

## Database

* **PostgreSQL**

PostgreSQL is the primary relational database for IATROS V1.

The application must not depend on Microsoft SQL Server/SSMS for the production architecture.

Database administration tools may be used for development and management, but the application's database target is PostgreSQL.

---

## Build and Dependency Management

### Frontend

Use the standard Node.js ecosystem and package management appropriate for the selected frontend setup.

### Backend

Use **Maven** for dependency management and builds.

---

## Version Control

The project must use **Git**.

The repository should be structured so that the project can be maintained publicly on GitHub without exposing:

* Secrets
* Passwords
* API keys
* Database credentials
* Private configuration
* Production environment variables

---

# 3. High-Level System Architecture

IATROS uses a client-server architecture.

```text
┌──────────────────────────────┐
│          IATROS UI           │
│      React + TypeScript      │
└──────────────┬───────────────┘
               │ HTTPS / REST
               ▼
┌──────────────────────────────┐
│       IATROS Backend         │
│         Spring Boot         │
│                              │
│ ┌──────────────────────────┐ │
│ │ Controllers / REST API    │ │
│ ├──────────────────────────┤ │
│ │ Services / Business Logic │ │
│ ├──────────────────────────┤ │
│ │ Repositories              │ │
│ ├──────────────────────────┤ │
│ │ Security / Authorization  │ │
│ └──────────────────────────┘ │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│         PostgreSQL           │
└──────────────────────────────┘
```

The frontend must not communicate directly with PostgreSQL.

All database access must occur through the backend.

---

# 4. Architectural Principles

## 4.1 Separation of Concerns

The frontend, backend, business logic, persistence, and database must remain clearly separated.

The frontend is responsible for presentation and user interaction.

The backend is responsible for:

* Business rules
* Validation
* Authorization
* Data access
* Security
* API contracts

The database is responsible for persistent data storage and relational integrity.

---

## 4.2 Backend as the Source of Truth

The backend is the authoritative source for:

* Permissions
* Business rules
* Data validation
* Appointment conflicts
* Doctor availability
* Patient authorization
* Medical-record access
* Clinic isolation

The frontend must not be trusted to enforce these rules.

---

## 4.3 API-First Communication

Frontend and backend communicate through defined REST API contracts.

The frontend must not depend on implementation details inside the backend.

API contracts should be documented and kept consistent with the backend implementation.

---

## 4.4 Modular Architecture

IATROS should be organized around business domains rather than creating one large undifferentiated application.

Core domains include:

* Authentication
* Clinics
* Users
* Doctors
* Staff
* Patients
* Medical Records
* Consultations
* Appointments
* Schedules
* Dashboards
* KPIs
* Settings
* Notifications where implemented

Future specialty modules should be able to extend the core without rewriting the patient and authentication foundations.

---

# 5. Backend Architecture

The backend should follow a layered architecture.

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Additional layers may be introduced where justified.

### Package structure

Code is organized **by module**, not by layer. Each module package holds its entities, repositories, services, controllers, and DTOs:

```text
com.iatros.backend
├── clinic/          # Clinic, ClinicSettings, clinic API
├── provisioning/    # operator command that creates a clinic and its owner (D-020)
├── user/            # AppUser, Role, UserStatus
├── staff/           # staff API: team members, invitations, activation status (D-034)
├── auth/            # sessions, login, invitations, password reset, UserToken
├── doctor/          # Doctor, Specialty, specialty API
├── schedule/        # DoctorSchedulePeriod, ScheduleException
├── patient/         # Patient, PatientDoctor (access relationships)
├── medicalrecord/   # MedicalRecord and clinical entries
├── consultation/    # Consultation
├── appointment/     # Appointment, status lifecycle
├── document/        # MedicalDocument metadata and storage
├── audit/           # AuditEvent (append-only)
├── activity/        # activity feed read from the audit trail (D-039)
├── kpi/             # KPI aggregates computed in SQL (D-031, D-039)
├── dashboard/       # one aggregated response per role dashboard (D-039)
├── access/          # AccessPolicy, DataScope: data-scope authorization (D-018)
├── security/        # Spring Security configuration, sessions, principal
└── shared/
    ├── error/       # ApiException hierarchy, GlobalExceptionHandler (ProblemDetail)
    ├── id/          # UUID v7 generator
    ├── i18n/        # Language
    ├── persistence/ # BaseEntity, AuditedEntity, TimestampedEntity, Versions (optimistic locking)
    ├── text/        # input trimming helpers
    ├── time/        # Clock bean
    └── web/         # PageResponse, PageRequests, SortWhitelist, SearchTerms, RequestIdFilter
```

Persistence rules (references by ID, explicit clinic-scoped repositories) are defined in `decisions.md` D-009. Services and code must obtain the current time from the injected `Clock`, never `Instant.now()` directly, so time-dependent rules are testable.

---

## 5.1 Controllers

Controllers are responsible for:

* Receiving HTTP requests
* Validating request structure
* Calling appropriate services
* Returning API responses

Controllers should not contain substantial business logic.

---

## 5.2 Services

Services contain business logic.

Examples:

* Creating an appointment
* Checking doctor availability
* Preventing appointment conflicts
* Assigning a patient to a doctor
* Checking medical-record access
* Managing staff roles
* Calculating KPIs

Business rules must not be duplicated unnecessarily across controllers.

---

## 5.3 Repositories

Repositories handle persistence operations.

They should abstract database interaction from the service layer.

---

## 5.4 Entities

Database entities represent persistent domain data.

Entities must not automatically be exposed directly as API responses.

DTOs should be used where appropriate to control data exposure and API contracts.

---

## 5.5 DTOs

DTOs should be used to:

* Control API payloads
* Prevent accidental exposure of sensitive fields
* Separate API contracts from database entities
* Support different request and response structures

---

# 6. Frontend Architecture

The frontend should be organized around reusable application features and components.

A conceptual structure is:

```text
src/
├── app/            # providers, router, query client
├── i18n/           # i18next setup + locales/{en,fr}
├── styles/         # design tokens, themes, global styles
├── shared/
│   ├── api/        # HTTP client, error mapping
│   ├── ui/         # design-system components
│   ├── forms/      # shared schema building blocks
│   ├── hooks/
│   └── lib/
├── layouts/
└── features/
    ├── auth/
    ├── dashboard/
    ├── calendar/
    ├── patients/
    ├── medical-records/
    ├── team/
    ├── doctors/
    ├── schedules/
    ├── activity/
    ├── clinic/
    └── account/
```

Each feature owns its pages, components, API hooks, and types (`decisions.md` D-042).

---

# 7. API Architecture

The API should be RESTful and resource-oriented.

Example resources include:

```text
/api/v1/auth
/api/v1/clinic
/api/v1/staff
/api/v1/doctors
/api/v1/patients
/api/v1/consultations
/api/v1/appointments
/api/v1/calendar
/api/v1/documents
/api/v1/dashboard
/api/v1/kpis
/api/v1/activity
```

The exact endpoint structure must be defined in `api.md`.

---

# 8. Authentication Architecture

IATROS uses Spring Security for backend authentication and authorization.

Authentication must establish the identity of the current user.

The authenticated context must provide enough information for the backend to determine:

* User identity
* Role
* Clinic
* Authorized data scope

IATROS uses server-side sessions stored in PostgreSQL (Spring Session JDBC), delivered to the browser as an `HttpOnly` cookie, with CSRF protection enabled. See `decisions.md` D-010 to D-012 and `security.md` §6.

---

# 9. Authorization Architecture

Authorization is enforced on the backend.

Every protected request must be evaluated against:

```text
Authenticated User
        ↓
Role
        ↓
Clinic
        ↓
Resource
        ↓
Authorized Scope
        ↓
Requested Action
```

This is particularly important for:

* Patients
* Medical records
* Consultations
* Appointments
* Doctors
* KPIs

A valid login alone does not grant access to all clinic data.

Implementation: role checks use `@PreAuthorize`; resource and data-scope checks go through `access.AccessPolicy` (`decisions.md` D-018).

---

# 10. Multi-Tenant Data Architecture

IATROS is logically clinic-based.

Each clinic's data must be isolated from other clinics.

Core entities should therefore maintain an appropriate relationship to the clinic.

Conceptually:

```text
Clinic
 ├── Users
 ├── Doctors
 ├── Staff
 ├── Patients
 ├── Appointments
 └── Medical Records
```

The backend must validate clinic ownership/association before returning or modifying protected resources.

The application must never rely solely on frontend filtering to achieve tenant isolation.

---

# 11. Patient Data Architecture

Patients belong to a clinic.

Patients may be associated with:

* A primary doctor
* Additional authorized doctors

This allows a patient to receive care from multiple doctors within the same clinic.

The authorization layer determines which doctors can access the patient's clinical information.

This model must be reflected consistently across:

* Patient APIs
* Medical records
* Consultations
* Appointments
* Search
* Dashboards
* KPIs

Implementation (`decisions.md` D-036): the `patient` package owns patients and their doctor links. `PatientService` handles identity, contact, and status; `PatientDoctorService` handles the owner-only access changes. Every entry point resolves the patient through `AccessPolicy.requirePatient`, and the list query applies the same scope in SQL (`AccessPolicy.dataScope`), so a doctor's search never sees unlinked patients. Linked doctors for a page of patients are loaded in a fixed number of batched queries (`PatientDoctorDirectory`), not one query per patient.

---

# 12. Medical Record Architecture

The medical record is a core domain but must remain generic.

The core record supports general medical information and consultation history.

Future specialty modules must extend the record without requiring a new patient-management system.

Conceptually:

```text
Patient
   │
   └── Medical Record
          │
          ├── Core Medical Information
          ├── Consultations
          ├── Documents
          │
          └── Future Specialty Extensions
```

Specialty-specific logic must not be embedded into the core medical-record model unless it is genuinely required by the core product.

Implementation (`decisions.md` D-037): every clinical entry point resolves the record through `MedicalRecordAccess`, which applies `AccessPolicy.requireClinicalAccess` (owners, linked doctors, never secretaries) and, for writes, refuses archived patients. Consultations reached by their own ID go through the same check on their record's patient. The four structured sections share `ClinicalEntryWriter` (save, version check, soft delete, identifier-only audit); `MedicalNoteService` adds author-only changes, and the `consultation` package owns consultations. Clinic-local "today" comes from `ClinicTime`.

---

# 13. Appointment Architecture

Appointments are first-class domain objects.

An appointment is associated with:

* Clinic
* Patient
* Doctor
* Date/time
* Duration
* Type
* Status
* Relevant notes

The backend is responsible for:

* Availability validation
* Conflict detection
* Appointment status transitions
* Authorization
* Rescheduling rules
* Cancellation rules

The calendar UI is only a representation of appointment data.

The calendar must not contain the authoritative appointment logic.

Implementation (`decisions.md` D-038): the `appointment` package owns appointments. `AppointmentService` handles booking, edits, rescheduling, status changes, and cancellation; `CalendarService` serves the calendar range and availability from `WorkingHoursService`. Access goes through `AccessPolicy` (`requireCanBook`, `requireAppointment`, `requireDoctorCalendar`, and the list's data scope in SQL). The entity enforces D-024 transitions and time validity, and the `appointment_no_overlap` exclusion constraint remains the final guard against double booking (D-005). Responses state what the caller may do (`editable`, `cancellable`, `nextStatuses`), and names for a page of appointments are loaded in batched queries (`AppointmentDirectory`). Every change is audited with identifiers only.

---

# 14. Doctor Schedule Architecture

Doctor schedules define availability.

The appointment service must consult schedule information when validating appointments.

The system must prevent appointments from being created outside permitted availability unless an explicitly authorized workflow allows it.

Implementation (`decisions.md` D-035): the `schedule` package owns weekly periods and exceptions. `WorkingHoursService` is the single availability calculation (weekly periods + extra availability − unavailability, in the clinic's time zone); the working-hours API and appointment validation both call it. Schedule rows carry no `clinic_id`, so every entry point first resolves the doctor within the caller's clinic through `AccessPolicy` (D-009).

Future external calendar integrations must interact with this domain through defined integration services rather than directly modifying database records.

---

# 15. Search, Filtering, Sorting, and Pagination

Search, filtering, sorting, and pagination are considered core platform behavior.

### Search

Relevant pages must provide search functionality.

### Filtering

Relevant data views must provide filtering appropriate to the domain.

### Sorting

Relevant tables and data views must support sorting.

### Pagination

Tables displaying potentially large datasets must use pagination.

The backend should support server-side pagination for datasets that can grow beyond a reasonable client-side size.

### Authorization

Search, filters, sorting, and pagination must operate only over records the current user is authorized to access.

The API must apply authorization before returning paginated results.

---

# 16. User Experience Architecture

UX is considered a cross-application requirement rather than an isolated design task.

Every major page must account for:

* Loading state
* Empty state
* Error state
* Success feedback
* Validation feedback
* Confirmation for consequential actions
* Clear primary actions
* Consistent navigation
* Consistent forms
* Consistent tables
* Consistent search/filter/sort behavior
* Pagination where applicable

The application should avoid unnecessary steps and duplicated workflows.

Common operations should feel consistent across modules.

---

# 17. State Management

Frontend state should be divided conceptually into:

### Server State

Data retrieved from the backend, such as:

* Patients
* Appointments
* Doctors
* Staff
* Medical records
* KPIs

### UI State

Temporary interface state, such as:

* Modal visibility
* Selected filters
* Calendar view
* Form state
* Theme
* Local UI preferences

The project should avoid placing all application state into one global store.

The simplest appropriate state-management solution should be used.

---

# 18. Error Handling

The backend must return consistent error responses.

The frontend must translate these responses into useful user-facing feedback.

Errors should:

* Be understandable
* Avoid exposing sensitive implementation details
* Provide actionable information where possible
* Preserve application stability

Unexpected internal errors must not expose stack traces or sensitive information to end users.

---

# 19. Validation

Validation occurs at multiple levels.

### Frontend

Frontend validation provides immediate UX feedback.

### Backend

Backend validation is authoritative.

### Database

Database constraints should protect fundamental data integrity.

No single validation layer should be assumed to be sufficient for all cases.

---

# 20. Database Migrations

Database schema changes must be version-controlled through a migration system.

Developers must not rely on manually modifying production database schemas.

Each schema change must have a corresponding migration.

IATROS uses Flyway with plain SQL migrations. Hibernate only validates the schema (`decisions.md` D-002).

---

# 21. Configuration

Environment-specific configuration must be externalized.

Examples include:

* Database connection information
* JWT/security secrets
* Email configuration
* External API credentials
* Storage configuration

Secrets must never be committed to Git.

Example environment-specific files containing secrets must be excluded through `.gitignore`.

---

# 22. File Storage

Medical documents must not be stored directly inside relational database fields as large binary objects unless a specific requirement justifies it.

The application should use an appropriate file-storage abstraction.

V1 uses a `DocumentStorage` abstraction: local filesystem in development, S3-compatible object storage in production (`decisions.md` D-032). Implemented so far: `LocalDocumentStorage` (directory from `IATROS_DOCUMENTS_DIR`; atomic writes under generated keys). The object-storage adapter is added with the production deployment; `DocumentService` only depends on the interface (D-044).

Access to medical documents must always pass through authorization checks.

---

# 23. Logging and Monitoring

The backend should provide structured application logging.

Logs should help diagnose:

* Authentication failures
* Application errors
* Important business failures
* Integration failures
* Unexpected system behavior

Logs must not contain sensitive medical information or credentials unnecessarily.

---

# 24. Testing Architecture

The project should include testing at multiple levels.

### Backend

* Unit tests
* Service tests
* Repository/integration tests
* Controller/API tests
* Authorization tests

### Frontend

* Component tests where useful
* Feature/workflow tests where appropriate

### Critical Security Tests

Specific tests must verify that:

* Doctors cannot access unauthorized patients.
* Doctors cannot access other doctors' restricted data.
* Secretaries cannot access medical records.
* Users cannot access another clinic's data.
* Unauthorized API calls are rejected.

---

# 25. Scalability

V1 is optimized for small clinics rather than massive healthcare networks.

However, the architecture should avoid unnecessary limitations.

The system should be capable of scaling through:

* Efficient database queries
* Proper indexing
* Pagination
* Stateless backend design where practical
* Clear domain boundaries
* Separation of frontend and backend
* Externalized file storage
* Appropriate caching where justified

Scaling mechanisms should only be introduced when they solve an actual problem.

---

# 26. Future Extensibility

The architecture should allow future functionality including:

* Patient accounts
* Patient portal
* Specialty modules
* Google Calendar integration
* Microsoft Outlook integration
* Notifications
* SMS/WhatsApp communication
* Billing
* Online payments
* Mobile applications
* Advanced analytics
* AI functionality

Future extensibility must not justify unnecessary abstraction in V1.

The simplest architecture that correctly supports current requirements should be preferred.

---

# 27. Architectural Decision Rule

When choosing between two technically valid approaches:

1. Prefer the simpler solution.
2. Prefer the solution consistent with existing documentation.
3. Prefer established framework conventions.
4. Avoid unnecessary dependencies.
5. Avoid premature optimization.
6. Preserve security and data integrity.
7. Preserve future extensibility where it can be achieved without significant complexity.

Developers and AI coding tools must not introduce major architectural changes without updating the relevant documentation.

---

# 28. Approved V1 Stack Summary

| Layer           | Technology                  |
| --------------- | --------------------------- |
| Frontend        | React + TypeScript (strict) |
| Frontend Build  | Vite                        |
| Routing         | React Router                |
| Server State    | TanStack Query              |
| i18n            | i18next + react-i18next     |
| Styling         | CSS Modules + design tokens |
| Backend         | Java 21 + Spring Boot 4     |
| API             | REST (`/api/v1`)            |
| Security        | Spring Security + Spring Session JDBC |
| ORM             | Spring Data JPA + Hibernate |
| Validation      | Bean Validation             |
| Migrations      | Flyway                      |
| Database        | PostgreSQL                  |
| Backend Build   | Maven                       |
| Version Control | Git                         |
| Repository      | GitHub                      |

This stack is the baseline for IATROS V1.

Any deviation from the approved stack must have a documented reason and should be approved before implementation.
