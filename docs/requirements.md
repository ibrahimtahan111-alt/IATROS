# IATROS — Requirements

## 1. Purpose

This document defines the functional and non-functional requirements for IATROS V1.

The requirements describe **what the system must do**, not how it must be implemented.

Implementation details belong in the architecture, database, API, and development documentation.

---

# 2. V1 Functional Requirements

## 2.1 Authentication

### AUTH-001 — User Login

The system must allow registered users to log in using their credentials.

The system must:

* Validate the user's credentials.
* Reject invalid credentials.
* Prevent access to protected application areas without authentication.
* Establish an authenticated session/token after successful login.

### AUTH-002 — Logout

Authenticated users must be able to log out.

After logout, protected resources must no longer be accessible using the invalidated authentication state.

### AUTH-003 — Password Reset

The system must provide a password-reset workflow.

The workflow must allow an eligible user to:

1. Request a password reset.
2. Receive a reset mechanism.
3. Set a new password.
4. Return to the application using the new credentials.

The implementation must not expose the existing password.

### AUTH-004 — Password Change

Authenticated users must be able to change their password.

### AUTH-005 — Account Status

The system must support account activation/deactivation.

A deactivated account must not be able to access the application.

### AUTH-006 — Role Assignment

Each application user must have an appropriate system role.

V1 roles:

* Owner
* Doctor
* Secretary

The system must enforce role-based access control.

---

# 3. Clinic Requirements

## CLINIC-001 — Clinic Creation

A clinic must have a defined organizational record.

The clinic record must contain the information required for the clinic to operate within IATROS.

## CLINIC-002 — Clinic Settings

The clinic owner must be able to view and modify permitted clinic settings.

## CLINIC-003 — Clinic Identity

The clinic name must be displayed within the application's main navigation/header.

## CLINIC-004 — Clinic Isolation

Clinic data must be isolated from other clinics.

A user belonging to one clinic must not be able to access another clinic's data.

## CLINIC-005 — V1 Organizational Model

IATROS V1 uses a clinic-based organizational model.

The V1 user interface does not provide multi-clinic management.

The underlying architecture should avoid preventing future expansion of the platform.

---

# 4. Staff Requirements

## STAFF-001 — Staff Listing

The clinic owner must be able to view the clinic's staff members.

## STAFF-002 — Add Staff Member

The clinic owner must be able to add a new team member.

The system must allow the owner to specify the appropriate role.

## STAFF-003 — Edit Staff Member

The clinic owner must be able to edit permitted staff information.

## STAFF-004 — Deactivate Staff Member

The clinic owner must be able to deactivate a staff member.

Deactivation must prevent the affected account from logging in.

## STAFF-005 — Staff Roles

V1 supports:

* Owner
* Doctor
* Secretary

Each role must have defined access boundaries.

## STAFF-006 — Doctor Information

Doctor accounts must support doctor-specific information, including:

* Primary specialty
* Additional specialty information
* Schedule/availability

---

# 5. Doctor Requirements

## DOCTOR-001 — Doctor Profile

Each doctor must have a profile containing the information required to identify and manage the doctor within the clinic.

## DOCTOR-002 — Primary Specialty

A doctor must be able to have a primary specialty.

The primary specialty should use structured system data rather than free-form text.

## DOCTOR-003 — Additional Specialty

A doctor may have additional specialty information.

Additional specialty information is represented as text in V1.

## DOCTOR-004 — Doctor Schedule

A doctor must have a configurable working schedule.

## DOCTOR-005 — Doctor Availability

The system must determine whether a doctor is available for an appointment based on the configured schedule.

## DOCTOR-006 — Doctor Data Isolation

A doctor must only be able to access information they are authorized to access.

Doctors must not automatically receive unrestricted access to all doctors' private information.

---

# 6. Patient Requirements

## PATIENT-001 — Patient Creation

Authorized clinic users must be able to create patient records.

## PATIENT-002 — Patient Profile

A patient profile must contain relevant personal and contact information.

V1 should avoid collecting unnecessary information.

## PATIENT-003 — Patient Search

Authorized users must be able to search for patients.

Search should support the primary identifying patient information used by the application.

## PATIENT-004 — Patient Filtering

Patient records must support appropriate filtering where useful.

## PATIENT-005 — Patient Sorting

Patient records must support appropriate sorting where useful.

## PATIENT-006 — Patient Clinic Association

Every patient must belong to a clinic.

## PATIENT-007 — Doctor Association

A patient may be associated with:

* A primary doctor
* Additional authorized doctors

A patient is not permanently restricted to a single doctor.

## PATIENT-008 — Patient Accounts

Patient accounts are not implemented in V1.

The architecture should allow a patient account and patient role to be introduced later.

---

# 7. Medical Record Requirements

## MED-001 — Medical Record

Every patient must have a medical record.

## MED-002 — Core Medical Information

The medical record must support:

* Allergies
* Chronic conditions
* Current medications
* Past medical history
* General medical notes

## MED-003 — Consultation History

The medical record must maintain a historical record of consultations/visits.

A consultation should contain:

* Date/time
* Doctor
* Reason for visit
* Diagnosis
* Clinical/treatment notes
* Follow-up information

## MED-004 — Historical Integrity

Existing consultation records must not be silently overwritten when a new consultation is created.

The medical record must preserve historical clinical information.

## MED-005 — Documents

Authorized users must be able to associate medical documents/files with the patient's record.

## MED-006 — Medical Record Access

Medical record access must follow the application's role and doctor authorization rules.

Unauthorized users must not be able to access medical records.

## MED-007 — Generic Foundation

The V1 medical record must remain generic.

Specialty-specific medical structures must not be hard-coded into the core medical record unless required for core functionality.

Future specialty modules must be able to extend the medical record.

---

# 8. Appointment Requirements

## APPT-001 — Calendar-First Interface

Appointments must primarily be managed through a calendar interface.

The calendar is the primary appointment-management experience.

## APPT-002 — Calendar Views

The system should support useful calendar views for daily, weekly, and monthly scheduling.

## APPT-003 — Doctor Filtering

Authorized users must be able to filter the calendar by doctor.

## APPT-004 — Create Appointment

Authorized users must be able to create an appointment.

An appointment must identify:

* Patient
* Doctor
* Date
* Start time
* End time/duration
* Appointment type
* Status
* Notes/reason for visit where applicable

## APPT-005 — Appointment Duration

Appointments must have a defined duration.

The default appointment duration is 30 minutes unless configured otherwise.

## APPT-006 — Appointment Editing

Authorized users must be able to edit appointment information.

## APPT-007 — Rescheduling

Authorized users must be able to reschedule appointments.

## APPT-008 — Cancellation

Authorized users must be able to cancel appointments.

Cancelled appointments must retain an appropriate historical status rather than being silently deleted.

## APPT-009 — Appointment Status

Appointments must support the following operational statuses:

* Scheduled
* Confirmed
* Waiting
* In consultation
* Completed
* Cancelled
* No-show

## APPT-010 — Conflict Prevention

The system must prevent conflicting appointments for the same doctor.

## APPT-011 — Availability Validation

The system must validate appointments against the doctor's configured availability.

## APPT-012 — Patient Appointment History

Authorized users must be able to view a patient's relevant appointment history.

## APPT-013 — Doctor Appointment Access

Doctors must be able to view and manage appointments they are authorized to access.

## APPT-014 — Secretary Appointment Access

Secretaries must be able to perform the appointment-management actions required for clinic booking operations.

## APPT-015 — External Calendars

Google Calendar and Microsoft Outlook integration are not part of V1.

The architecture should not prevent future integration.

---

# 9. Dashboard Requirements

## DASH-001 — Role-Based Dashboard

The system must provide dashboard content appropriate to the authenticated user's role.

## DASH-002 — Owner Dashboard

The owner dashboard must provide clinic-level operational information.

This may include:

* Today's activity
* Appointment information
* Patient activity
* Doctor activity
* Clinic KPIs

## DASH-003 — Doctor Dashboard

The doctor dashboard must focus on the doctor's own activity.

It may include:

* Today's patients
* Today's appointments
* Patient information
* Patient distribution
* Condition-related information
* Appointment details
* Personal KPIs

## DASH-004 — Data Access

Dashboard information must respect the same authorization rules as the underlying data.

A dashboard must not reveal information a user is otherwise unauthorized to access.

---

# 10. KPI Requirements

## KPI-001 — Clinic KPIs

The system must provide relevant operational KPIs for the clinic owner.

## KPI-002 — Doctor KPIs

The system must provide relevant doctor-level KPIs to authorized users.

## KPI-003 — Data Accuracy

KPIs must be calculated from actual application data.

The system must not store duplicated KPI values when they can safely be calculated from authoritative records.

## KPI-004 — Access Control

Users must only see KPI information they are authorized to access.

---

# 11. Search, Filtering, and Sorting

## DATA-001 — Search

Relevant tables and lists must provide search functionality where appropriate.

## DATA-002 — Filtering

Relevant data views must provide filtering where appropriate.

## DATA-003 — Sorting

Relevant tabular data must provide sorting where appropriate.

## DATA-004 — Consistency

Search, filtering, and sorting interactions should follow consistent patterns throughout the application.

---

# 12. User Interface Requirements

## UI-001 — Responsive Web Application

IATROS must operate as a web application and provide a usable interface across supported desktop and tablet screen sizes.

Small (phone-sized) browser screens must remain usable for viewing and quick actions. A native mobile application is not part of V1.

## UI-002 — Consistent Navigation

Navigation must remain consistent throughout the application.

## UI-003 — Role-Based Navigation

Users should only see navigation items corresponding to functionality they are authorized to access.

## UI-004 — Clear Feedback

The system must provide clear feedback for:

* Successful actions
* Validation errors
* Failed operations
* Permission errors
* Loading states
* Empty states

## UI-005 — Confirmation

Destructive or consequential actions should require appropriate confirmation.

Examples include:

* Deactivating users
* Cancelling appointments
* Removing records where removal is permitted

---

# 13. Localization Requirements

## I18N-001 — Supported Languages

V1 must support:

* English
* French

## I18N-002 — Externalized Text

User-facing application text must not be hard-coded directly into components in a way that prevents translation.

## I18N-003 — Language Switching

Users must be able to switch between supported languages through the application's language settings.

## I18N-004 — Consistency

Translated interfaces must preserve the same functionality and access rules.

---

# 14. Theme Requirements

## THEME-001 — Light Mode

IATROS must support a light theme.

## THEME-002 — Dark Mode

IATROS must support a dark theme.

## THEME-003 — Consistency

Both themes must maintain readable text, usable controls, and clear visual hierarchy.

---

# 15. Security Requirements

## SEC-001 — Authentication

Protected resources must require authentication.

## SEC-002 — Authorization

The backend must enforce authorization independently of frontend visibility.

Hiding a UI element is not sufficient to protect a resource.

## SEC-003 — Clinic Isolation

Users must not be able to access data belonging to another clinic.

## SEC-004 — Role Enforcement

The backend must enforce role permissions.

## SEC-005 — Doctor Data Isolation

Doctor access must be restricted according to the doctor's authorized data scope.

## SEC-006 — Sensitive Data Protection

Medical information must only be accessible to authorized users.

## SEC-007 — Password Security

Passwords must never be stored in plaintext.

## SEC-008 — Secrets

Application secrets must not be stored directly in source code or committed to the repository.

## SEC-009 — Auditability

Important changes to sensitive clinic and medical information should be attributable to the user who performed the action.

The detailed audit model will be defined in the security and architecture documentation.

---

# 16. Data Integrity Requirements

## DATAINT-001 — Referential Integrity

Relationships between core entities must remain valid.

## DATAINT-002 — Appointment Integrity

The system must prevent invalid appointment states and scheduling conflicts.

## DATAINT-003 — Medical History Integrity

Historical medical information must not be accidentally overwritten by later interactions.

## DATAINT-004 — Validation

User input must be validated on both the frontend and backend where appropriate.

## DATAINT-005 — Error Handling

Failed operations must not leave the database in an inconsistent state.

---

# 17. Performance Requirements

## PERF-001 — Normal Operations

Common application operations should respond within a reasonable time under expected V1 usage.

## PERF-002 — Database Queries

Database queries should avoid unnecessary retrieval of large datasets.

## PERF-003 — Pagination

Large datasets should use pagination or another appropriate strategy rather than loading unlimited records into the client.

## PERF-004 — Scalability

The architecture should allow IATROS to grow beyond the initial small-clinic use case without requiring a complete rewrite of the application.

---

# 18. Reliability Requirements

## REL-001 — Error Recovery

The application must handle expected errors gracefully.

## REL-002 — No Silent Data Loss

User actions must not silently delete or overwrite important data.

## REL-003 — Transactional Operations

Operations involving multiple related database changes must preserve data consistency.

## REL-004 — Auditability

Important sensitive changes should be traceable to the responsible user.

---

# 19. V1 Boundaries

The following are explicitly excluded from the V1 functional requirements:

* Patient portal
* Patient login
* Online patient appointment requests
* Mobile application
* Google Calendar integration
* Microsoft Outlook integration
* Online payments
* Subscription billing implementation
* Advanced insurance management
* Advanced laboratory management
* Advanced imaging management
* Specialty-specific medical modules
* Advanced prescription management
* AI-powered clinical features
* Multi-clinic management

These may become future requirements and must not be implemented as part of V1 unless the product documentation is intentionally revised.

---

# 20. Requirement Priority

Requirements should generally be implemented in this order:

### Priority 1 — Foundation

* Authentication
* Clinic structure
* Users/staff
* Roles and authorization
* Database integrity
* Security

### Priority 2 — Core Clinic Workflow

* Patients
* Medical records
* Doctors
* Doctor schedules
* Appointments/calendar

### Priority 3 — Operational Experience

* Dashboards
* KPIs
* Search
* Filtering
* Sorting

### Priority 4 — User Experience Refinement

* Localization review and completeness
* Theme polish
* Responsive refinement
* UX refinement

Priority 4 is refinement only. The localization and theme **foundations**, plus loading/empty/error states, are built with the application shell and are required for every feature from the start (see `roadmap.md` Phase 4).

---

# 21. Requirement Change Rule

Requirements are not to be changed casually during implementation.

If a new requirement is discovered:

1. Identify the affected requirement.
2. Determine whether it belongs in V1 or the future roadmap.
3. Update the appropriate documentation.
4. Check its effect on the architecture and database.
5. Only then modify the implementation.

This prevents feature creep and repeated redesign of the application.

---

# 22. Definition of Done

A requirement is considered implemented only when:

* The intended functionality exists.
* Authorized users can use it.
* Unauthorized users cannot access it.
* Backend validation exists where required.
* Relevant error states are handled.
* Data is persisted correctly.
* Existing functionality is not broken.
* The implementation follows the current architecture.
* Relevant documentation is updated if behavior changed.

A feature is not considered complete merely because its frontend interface exists.
