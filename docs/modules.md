# IATROS — Business Modules

## 1. Purpose

This document defines the major business modules of IATROS, the responsibilities of each module, their boundaries, and their relationships.

The goal is to keep the application modular so that:

* each business area has a clear responsibility;
* features do not become unnecessarily coupled;
* authorization can be applied consistently;
* the application remains maintainable as it grows;
* future specialty-specific modules can be added without redesigning the core system.

This document complements `product.md`, `requirements.md`, `roles-and-permissions.md`, `architecture.md`, and `database.md`.

---

# 2. Module Principles

IATROS should follow these principles:

### 2.1 Clear responsibilities

Each module should own a specific business capability.

For example:

* Patients manage patient identity and clinic-level patient information.
* Medical Records manage clinical information.
* Appointments manage scheduled interactions.
* Doctor Schedules manage when doctors are available.

These concepts should not be mixed unnecessarily.

### 2.2 Business rules belong to the backend

Modules may expose functionality through the frontend, but their important business rules must be enforced by the backend.

The frontend must never be the only place where a rule is enforced.

### 2.3 Modules communicate through defined boundaries

A module may depend on another module when there is a legitimate business relationship.

Modules should avoid directly manipulating another module's internal data.

### 2.4 Authorization applies inside every module

Every module must verify:

1. who the user is;
2. which clinic they belong to;
3. what role they have;
4. what specific data they are authorized to access.

A user must never gain access simply because a record is technically available through an API.

### 2.5 Future extensibility

The core modules should provide stable foundations for future functionality.

Specialty-specific functionality should extend the core rather than duplicate it.

---

# 3. V1 Module Map

IATROS V1 consists of the following primary modules:

1. Authentication
2. Clinic & Settings
3. Dashboard
4. Staff Management
5. Doctor Management
6. Patient Management
7. Medical Records
8. Consultations
9. Appointments & Calendar
10. Doctor Schedules
11. KPIs & Clinic Activity
12. Documents
13. Shared UX & Data Services

Some of these modules may share screens in the frontend, but their business responsibilities should remain conceptually separated.

---

# 4. Authentication Module

## Responsibility

Handles user authentication and account security.

## V1 functionality

* Login
* Logout
* Forgot password
* Reset password
* Password change
* Authentication state
* Session/token handling
* Basic account information

## Users

* Clinic Owner
* Doctor
* Secretary

## Important rules

Authentication determines **who the user is**.

It does not determine everything the user can access.

Authorization remains the responsibility of the security/access-control layer.

## Dependencies

Authentication is foundational and is used by all protected modules.

---

# 5. Clinic & Settings Module

## Responsibility

Manages the clinic's organizational information and configuration.

## V1 functionality

* Clinic profile
* Clinic name
* Contact information
* Address
* General clinic settings
* Working/business settings where applicable
* Application preferences
* Localization preferences
* Theme preferences

## Access

Owner:

* Full access

Doctor:

* No clinic administration access

Secretary:

* No clinic administration access unless explicitly granted by a future permission model

## Dependencies

The clinic is the primary organizational boundary for the rest of the application.

Most business entities belong to a clinic directly or indirectly.

---

# 6. Dashboard Module

## Responsibility

Provides role-specific summaries of important clinic information.

The dashboard is a presentation module rather than the owner of the underlying business data.

## Owner Dashboard

May display:

* Today's appointments
* Patient activity
* Doctor activity
* Appointment statistics
* Clinic KPIs
* Doctor performance/activity
* Recent clinic activity
* Relevant operational information

## Doctor Dashboard

May display:

* Today's appointments
* Total patients relevant to the doctor
* Patient information relevant to their authorized patients
* Upcoming appointments
* Own activity
* Own KPIs
* Relevant consultation information

## Secretary

No dedicated dashboard in V1 unless required by the final UI specification.

The secretary's primary workflow is the calendar and booking process.

## Important rule

The dashboard must never bypass the permissions of the underlying modules.

For example, a doctor cannot see another doctor's private information simply because it appears in a dashboard statistic.

---

# 7. Staff Management Module

## Responsibility

Manages clinic staff accounts and their roles.

## V1 functionality

* View staff
* Add team member
* Edit team member
* Assign role
* View staff status
* Activate/deactivate staff
* Manage staff profile information
* Manage relevant account information

## Roles

* Owner
* Doctor
* Secretary

## Access

Owner:

* Full staff management

Doctor:

* Own profile only

Secretary:

* No staff administration

## Important rule

Deactivating a staff member should not delete historical records created by that user.

Historical appointments, consultations, audit records, and other relevant information must remain attributable to the original user.

---

# 8. Doctor Management Module

## Responsibility

Manages information specific to doctors.

## V1 functionality

* Doctor profile
* Name and professional information
* Primary specialty
* Additional specialty text
* Contact information where applicable
* Doctor status
* Doctor working schedule
* Doctor availability
* Doctor appointments
* Authorized patient relationships
* Doctor-specific KPIs

## Specialty model

Each doctor has:

* one primary specialty;
* optional additional specialty information stored as text.

The initial implementation should remain simple.

Future specialty-specific modules may provide structured specialty functionality.

## Access

Owner:

* Manage clinic doctors

Doctor:

* View/edit own profile according to allowed fields
* Manage own relevant information

Secretary:

* May access the doctor information necessary for appointment booking/calendar workflows

---

# 9. Patient Management Module

## Responsibility

Manages patient identity and clinic-level patient information.

## V1 functionality

* Create patient
* View patient
* Edit patient
* Search patients
* Filter patients
* Sort patients
* Paginate patient tables
* View patient profile
* Associate patient with doctor
* Assign primary doctor
* Manage authorized doctor relationships
* View relevant patient activity

## Patient ownership model

Patients belong to the clinic.

A patient is not permanently owned by one doctor.

A patient may have:

* one primary doctor;
* one or more additional authorized doctors.

This allows a patient to receive care from multiple doctors without creating duplicate patient records.

## Access

Owner:

* Full patient access

Doctor:

* Authorized patients only

Secretary:

* Patient information required for booking/calendar operations

The secretary must not automatically receive unrestricted access to medical records.

## Dependencies

Patient Management is referenced by:

* Medical Records
* Consultations
* Appointments
* Doctor Management
* Dashboards
* KPIs

---

# 10. Medical Records Module

## Responsibility

Manages the patient's core medical record.

Every patient must have a medical record.

## V1 functionality

The core medical record includes:

* Allergies
* Chronic conditions
* Current medications
* Past medical history
* General medical notes
* Consultation history
* Diagnoses
* Clinical notes
* Treatment notes
* Follow-up information
* Medical documents

## Access

Owner:

* Full access

Doctor:

* Authorized patients only

Secretary:

* No medical-record access

## Design principle

The medical record must remain generic.

It should provide a foundation that future medical specialties can extend.

For example, a future cardiology module may add cardiology-specific information without modifying the fundamental patient identity model.

## Dependencies

Medical Records depend on:

* Patients
* Doctors
* Consultations
* Documents

---

# 11. Consultations Module

## Responsibility

Represents clinical encounters between a doctor and a patient.

## V1 functionality

A consultation may contain:

* Patient
* Doctor
* Date/time
* Appointment reference where applicable
* Reason for visit
* Diagnosis
* Clinical notes
* Treatment notes
* Follow-up notes
* Related documents

## Relationship with appointments

An appointment represents a scheduled event.

A consultation represents the clinical encounter.

Therefore:

**Appointment ≠ Consultation**

An appointment may result in a consultation, but the two concepts should remain separate.

## Access

Owner:

* Full access

Doctor:

* Authorized patient consultations

Secretary:

* No clinical consultation access

---

# 12. Appointments & Calendar Module

## Responsibility

Manages scheduled appointments and the primary calendar experience.

## V1 functionality

* Calendar view
* Day view
* Week view
* Month view
* Doctor filtering
* Patient selection
* Doctor selection
* Appointment creation
* Appointment editing
* Rescheduling
* Cancellation
* Appointment status changes
* Appointment type
* Duration
* Reason for visit
* Notes
* Conflict detection
* Availability validation

## Appointment statuses

* Scheduled
* Confirmed
* Waiting
* In consultation
* Completed
* Cancelled
* No-show

## Access

Owner:

* Full clinic calendar

Doctor:

* Own appointments and authorized relevant data

Secretary:

* Booking and calendar management

## Important rules

The backend must enforce:

* doctor availability;
* appointment conflicts;
* valid appointment times;
* clinic ownership;
* user authorization.

Cancelled appointments must remain part of the historical record rather than being silently deleted.

## Future integrations

* Google Calendar
* Microsoft Outlook Calendar

These are not part of V1.

---

# 13. Doctor Schedules Module

## Responsibility

Defines when doctors are available for appointments.

## V1 functionality

* Weekly working schedule
* Working days
* Working periods
* Availability
* Schedule editing
* Schedule exceptions where required
* Validation against appointment creation

A doctor may have multiple working periods in the same day.

For example:

* 09:00–12:00
* 14:00–18:00

## Access

Owner:

* Manage all doctor schedules

Doctor:

* Manage/view own schedule according to permissions

Secretary:

* View/use schedule information necessary for booking

## Dependencies

Appointments depend on doctor availability.

The appointment module must use schedule information when validating bookings.

---

# 14. KPIs & Clinic Activity Module

## Responsibility

Provides operational and performance information.

## Clinic KPIs

Owner-level information may include:

* Appointment volume
* Completed appointments
* Cancelled appointments
* No-shows
* Patient activity
* Doctor activity
* Other relevant clinic operational metrics

## Doctor KPIs

Doctors may see their own relevant metrics.

Examples:

* Appointments
* Completed consultations
* Patient activity
* Other doctor-specific operational metrics

## Important rule

KPIs must respect authorization.

A doctor must not receive unrestricted clinic-wide or other-doctor private KPI data.

## Architecture principle

KPIs should be calculated from authoritative business data rather than manually maintained counters wherever possible.

---

# 15. Documents Module

## Responsibility

Manages medical and other relevant documents associated with IATROS records.

## V1 functionality

* Upload document
* View document metadata
* Associate document with a patient
* Associate document with a medical record or consultation
* Download/view document where authorized
* Remove/archive document according to the final deletion policy

## Storage principle

The database stores document metadata.

Actual files should be stored through a dedicated storage abstraction.

The application should not tightly couple the medical-record system to a specific storage provider.

## Security

Documents are sensitive data.

Every document operation must verify authorization.

A user must not be able to access a document merely by knowing its URL or identifier.

---

# 16. Shared UX & Data Services

These are cross-cutting capabilities used throughout the application.

They are not necessarily standalone business modules in the navigation.

## Search

Relevant pages should provide search functionality appropriate to the data.

Search must operate only on authorized data.

## Filtering

Relevant pages should provide filters appropriate to the dataset.

Examples:

* Doctor
* Date
* Status
* Specialty
* Patient
* Appointment type

## Sorting

Tables should support sorting where useful.

Sorting must be performed against the accessible dataset.

## Pagination

Every table displaying potentially multiple records must use pagination.

The frontend should not load unlimited records simply to display a table.

## Loading states

Pages and components should provide clear loading feedback.

## Empty states

Empty datasets should explain what is empty and, where appropriate, provide a relevant action.

## Error states

Errors should be understandable and should not expose internal implementation details.

## Confirmation

Consequential actions should use confirmation where appropriate.

Examples:

* Deactivating a staff member
* Cancelling an appointment
* Removing a document
* Other destructive actions

---

# 17. Module Dependencies

The high-level dependency structure is:

```text
Authentication
      │
      ▼
Clinic / Settings
      │
      ├──────────────┐
      ▼              ▼
Staff            Doctors
                     │
                     ▼
              Doctor Schedules
                     │
                     ▼
Patients ───────► Appointments
   │                  │
   ▼                  ▼
Medical Records ◄─ Consultations
   │
   ▼
Documents

Patients + Doctors + Appointments + Consultations
                    │
                    ▼
               Dashboards
                    │
                    ▼
                  KPIs
```

This represents business relationships, not necessarily direct code dependencies.

---

# 18. Navigation-Level Modules

The exact final navigation will be defined in `ui-ux.md`, but the main application areas are expected to revolve around:

* Dashboard
* Calendar
* Patients
* Medical Records
* Doctors / Team
* Schedules
* KPIs / Activity
* Settings

Some areas may be combined in the UI while remaining separate in the backend.

For example:

* Doctors and Staff may appear under one Team section.
* Medical Records may be accessed primarily through a Patient profile.
* Consultations may appear inside the Medical Record rather than as a top-level navigation item.

The UI should prioritize natural workflows rather than exposing every backend module as a navigation item.

---

# 19. V1 vs Future Modules

## V1

* Authentication
* Clinic & Settings
* Dashboard
* Staff Management
* Doctor Management
* Patient Management
* Medical Records
* Consultations
* Appointments & Calendar
* Doctor Schedules
* KPIs & Clinic Activity
* Documents
* Shared UX/data capabilities

## Future

Potential future modules include:

* Patient Portal
* Patient Accounts
* Online Appointment Requests
* Google Calendar Integration
* Microsoft Outlook Integration
* Online Payments
* SaaS Subscription/Billing
* Advanced Prescription Management
* Laboratory Management
* Imaging Management
* Insurance Management
* Notifications
* Mobile Application
* AI-assisted clinical functionality
* Specialty-specific modules
* Multi-clinic management

These should not be implemented simply because the architecture allows them.

---

# 20. Future Specialty Modules

Specialty-specific functionality is an important long-term extension point.

Examples could include:

* Cardiology
* Dentistry
* Dermatology
* Ophthalmology
* Pediatrics
* Gynecology
* Orthopedics

Each specialty module should build on:

* the existing patient;
* doctor;
* medical record;
* consultation;
* appointment;
* document;

models.

It should not create an entirely separate patient-management system.

### Example

A future cardiology module could add:

* cardiac measurements;
* cardiovascular history;
* ECG-related records;
* specialty-specific consultation information.

The underlying patient and medical-record foundation remains shared.

---

# 21. Module Ownership Rule

Each module should have a clearly defined owner for its business logic.

Examples:

* Patient rules → Patient module
* Appointment conflict rules → Appointment module
* Doctor availability → Schedule module
* Clinical record rules → Medical Record module
* Staff role management → Staff/Authorization layer
* KPI calculation → KPI module

Other modules may request information or operations from the owning module, but should not bypass its business rules.

---

# 22. Module Implementation Rule

The backend should be organized around business domains rather than one large collection of unrelated controllers and services.

A module should generally contain its own:

* Controllers
* Services
* Repositories
* DTOs
* Validators
* Domain/business logic
* Tests

Shared infrastructure may be placed outside individual modules where appropriate.

The exact Java package structure will be defined in `development-rules.md`.

---

# 23. Source-of-Truth Rule

The hierarchy of product definition is:

1. `product.md` — What IATROS is
2. `requirements.md` — What it must do
3. `roles-and-permissions.md` — Who can do what
4. `architecture.md` — How the system is structured
5. `database.md` — How data is represented
6. `modules.md` — How business responsibilities are separated
7. `ui-ux.md` — How users interact with the system
8. `design-system.md` — How the interface looks and behaves visually
9. `api.md` — How modules communicate
10. `security.md` — How sensitive operations are protected
11. `decisions.md` — Resolved technical and product decisions
12. `development-rules.md` — How the code is implemented

If a future implementation conflicts with these documents, the documents must be updated deliberately before changing the implementation.

---

# 24. Final Module Principle

IATROS should be built as a **modular clinic-management platform**, not as a collection of disconnected CRUD screens.

The core model should remain:

**Clinic → Staff/Doctors → Patients → Medical Records → Consultations → Appointments → Schedules → Activity/KPIs**

while maintaining strict authorization and clear business boundaries.

The architecture should be simple enough for V1 while providing a stable foundation for future specialty modules and additional SaaS capabilities.
