# IATROS — Roles and Permissions

## 1. Purpose

This document defines the access model for IATROS V1.

The system uses **role-based access control (RBAC)** combined with clinic and doctor-level data boundaries.

Permissions must be enforced by the backend. Frontend restrictions are for user experience and must never be treated as the primary security mechanism.

---

# 2. V1 Roles

IATROS V1 contains three active application roles:

1. **Clinic Owner**
2. **Doctor**
3. **Secretary**

A **Patient** role may exist in the architecture for future functionality but is not active in V1.

---

# 3. Core Access Principles

## 3.1 Least Privilege

Users should only have access to the information and actions required for their role.

## 3.2 Backend Enforcement

Every protected operation must be authorized by the backend.

A user must not gain access simply by:

* Calling an API endpoint directly
* Modifying frontend requests
* Manipulating URL parameters
* Modifying client-side state
* Hiding/showing UI elements

## 3.3 Clinic Isolation

A user must only access resources belonging to their clinic.

For example:

```text
Clinic A
 ├── Owner A
 ├── Doctor A1
 ├── Doctor A2
 └── Secretary A

Clinic B
 ├── Owner B
 ├── Doctor B1
 └── Secretary B
```

Users from Clinic A must never access Clinic B's data.

## 3.4 Doctor Data Isolation

Doctors do not automatically receive access to every doctor's private information.

A doctor should primarily access:

* Their own profile
* Their own schedule
* Their own appointments
* Patients they are authorized to treat/access
* Medical information associated with those authorized patients
* Their own relevant KPIs

## 3.5 Owner Access

The clinic owner has full administrative access to their clinic.

This includes managing:

* Staff
* Doctors
* Patients
* Appointments
* Clinic settings
* Clinic-wide dashboards and KPIs
* Relevant medical records, subject to the system's security/audit model

## 3.6 Secretary Access

The secretary is intended to support clinic operations, particularly:

* Patient booking
* Calendar management
* Appointment administration

The secretary must not automatically receive access to clinical medical records.

---

# 4. Permission Model

IATROS V1 uses a simple module-level access model.

The initial model distinguishes primarily between:

* **Access**
* **No access**

Where a module provides actions with different risk levels, more specific action-level authorization may be required.

The application must not introduce unnecessarily complicated permission management in V1.

---

# 5. Permission Matrix

| Module / Area           | Owner                        | Doctor                       | Secretary                    |
| ----------------------- | ---------------------------- | ---------------------------- | ---------------------------- |
| Dashboard               | Full                         | Own                          | No                           |
| Clinic Settings         | Full                         | No                           | No                           |
| Staff Management        | Full                         | No                           | No                           |
| Doctor Management       | Full                         | Own profile                  | No                           |
| Doctor Schedules        | Full                         | Own schedule                 | Calendar-related access      |
| Patients                | Full                         | Authorized patients          | Booking-related access       |
| Medical Records         | Full                         | Authorized patients          | No                           |
| Calendar                | Full                         | Own appointments             | Full booking/calendar access |
| Appointments            | Full                         | Own appointments             | Full booking access          |
| Clinic KPIs             | Full                         | No                           | No                           |
| Doctor KPIs             | Full                         | Own KPIs                     | No                           |
| Search                  | According to accessible data | According to accessible data | According to accessible data |
| Filtering               | According to accessible data | According to accessible data | According to accessible data |
| Sorting                 | According to accessible data | According to accessible data | According to accessible data |
| Clinic-level Staff Data | Full                         | No                           | No                           |
| Patient Accounts        | Future                       | Future                       | Future                       |

"According to accessible data" means that search, filtering, and sorting must never expose records outside the user's existing authorization scope.

---

# 6. Owner Permissions

The clinic owner has full administrative access to their clinic.

The owner can:

### Staff

* View staff
* Add staff
* Edit staff
* Deactivate staff
* Assign supported roles

### Doctors

* View doctors
* Add doctors
* Edit doctors
* Manage doctor information
* Manage doctor specialties
* Configure doctor schedules

### Patients

* Create patients
* View patients
* Edit patients
* Search patients
* Filter patients
* Sort patient records
* View and maintain every patient's medical record; record consultations only with a doctor profile (D-033, D-037)
* View, upload, and archive any patient's documents (D-044)
* Manage patient-doctor associations (primary doctor, authorized doctors)
* Archive and restore patients

### Appointments

* View all clinic appointments
* Create appointments
* Edit appointments
* Reschedule appointments
* Cancel appointments
* Change appointment status
* View appointment history

### Clinic

* View clinic information
* Edit clinic settings
* View clinic activity
* View clinic KPIs

---

# 7. Doctor Permissions

Doctors have access to their own operational information and patients they are authorized to access.

### Doctor Profile

A doctor can:

* View their own profile
* Edit permitted profile information
* View their specialty information
* View their own schedule

A doctor cannot manage other doctors.

### Patients

A doctor can:

* View authorized patients
* Search authorized patients
* Filter authorized patients
* Sort authorized patients
* View relevant patient profiles
* View and maintain the medical records of authorized patients (allergies, conditions, medications, history, notes)
* Record consultations as themselves, and edit their own consultations and notes only (`decisions.md` D-037)
* View and upload documents of authorized patients, and archive the documents they uploaded (D-044)
* Create patients, with themselves as primary doctor
* Edit the identity and contact details of authorized patients

A doctor must not be able to search for or access patients outside their authorization scope. Doctors cannot grant or remove other doctors' access, change a patient's primary doctor, or archive patients; these are owner actions (`decisions.md` D-036).

### Appointments

A doctor can:

* View their appointments
* Create appointments for themselves, only with patients they are authorized for
* Edit, reschedule, and cancel their own appointments
* Update the status of their own appointments
* View relevant appointment history

A doctor cannot create or modify appointments for another doctor (`decisions.md` D-027). Another doctor's appointments, calendar, and availability are not visible to them (`404`), and a patient's next and last appointment are computed from their own appointments only (D-038).

Appointment status changes follow the transition rules in `decisions.md` D-024. A doctor can start the consultation of their own appointment from the calendar; it is linked to that appointment (D-038).

### Dashboard

A doctor can view:

* Their own dashboard
* Their own appointments
* Their own patient activity
* Their own relevant KPIs

A doctor cannot view clinic-wide administrative KPIs or another doctor's private dashboard information.

In practice (`decisions.md` D-039): the doctor dashboard and doctor KPIs are always the caller's own; requesting another doctor's KPIs or activity returns `404`. The activity feed shows only events the doctor performed or that concern their appointments, patients, schedule, or profile, and names patients only when the doctor is linked to them. Clinical alerts on the dashboard schedule are shown only for linked patients. An owner who is also a doctor (D-033) has both dashboards.

---

# 8. Secretary Permissions

The secretary is primarily an operational role.

### Calendar

The secretary can:

* View the clinic calendar
* Filter the calendar by doctor
* Create appointments
* Edit appointments
* Reschedule appointments
* Cancel appointments
* Update booking-related appointment information
* View appointment status

### Patients

The secretary can access the patient information necessary to perform booking operations.

This includes:

* Searching for existing patients
* Creating a patient when required for booking
* Viewing basic patient information
* Updating permitted administrative/contact information

The exact patient fields available to secretaries are defined in `decisions.md` D-029. Secretaries see a patient's primary doctor but not the list of other authorized doctors, and cannot archive patients or change doctor access (D-036).

Secretaries can book appointments for any doctor in the clinic, but cannot set `IN_CONSULTATION` or `COMPLETED` statuses (`decisions.md` D-024). Appointment notes are administrative and visible to them; whether a consultation was recorded for an appointment is not (D-038).

The secretary does not receive unrestricted access to clinical medical records.

### Medical Records

The secretary does not have access to medical records in V1, including medical documents.

### Staff

The secretary cannot manage staff accounts.

### Clinic Settings

The secretary cannot manage clinic settings.

### KPIs

The secretary does not have access to clinic or doctor performance KPIs, dashboards, or the activity feed (`403 FORBIDDEN`, D-039). Their home page is the calendar.

---

# 9. Patient Role — Future

A Patient role may exist in the architecture but is not active in V1.

Future patient functionality may include:

* Login
* Viewing appointments
* Requesting appointments
* Receiving notifications
* Updating permitted personal information
* Accessing permitted portions of their medical record

These permissions must be explicitly designed before the Patient role becomes active.

---

# 10. Patient-Doctor Access Model

Patients belong to the clinic.

A patient can have:

* One primary doctor
* Zero or more additional authorized doctors

Example:

```text
Patient
│
├── Clinic: Clinic A
├── Primary Doctor: Doctor A
│
└── Authorized Doctors
    ├── Doctor A
    └── Doctor B
```

Doctor A and Doctor B may both access the patient's relevant information according to their authorization.

A doctor who is not associated with the patient must not automatically gain access to that patient's medical record merely because they work at the same clinic.

The exact authorization mechanism must be implemented consistently across:

* Patient endpoints
* Medical-record endpoints
* Consultation endpoints
* Appointment endpoints
* Search
* Filtering
* Dashboards
* KPIs

---

# 11. Data Visibility Rules

The user's role determines their maximum access scope.

The general hierarchy is:

```text
Clinic Owner
    ↓
Entire clinic

Doctor
    ↓
Authorized clinical scope

Secretary
    ↓
Operational/booking scope
```

The system must never expand a user's access scope simply because the user can discover a record through search, filtering, sorting, direct URLs, or API calls.

---

# 12. Search, Filter, Sort and Pagination Authorization

Search, filtering, sorting, and pagination are considered part of the normal data-access layer.

They must operate only over records the authenticated user is authorized to access.

For example:

A doctor searching for a patient must receive results only from the doctor's authorized patient scope.

A secretary searching for a patient must receive only the patient information permitted for booking operations.

Pagination must not become a way to access records outside the user's authorization scope.

---

# 13. UI Authorization

The frontend should hide actions that a user cannot perform.

For example:

* A doctor should not see "Manage Staff".
* A secretary should not see "Medical Records".
* A doctor should not see clinic-wide administrative KPI controls.

However, UI hiding is only a usability mechanism.

The backend must independently reject unauthorized operations.

---

# 14. Sensitive Actions

Certain operations require stronger authorization checks.

Examples include:

* Deactivating a staff member
* Changing staff roles
* Modifying clinic settings
* Accessing medical records
* Editing clinical information
* Cancelling appointments
* Deleting or permanently removing data where deletion is permitted

These actions should be validated server-side.

Important sensitive actions should also be auditable.

---

# 15. Permission Evolution

The V1 permission system should remain simple.

IATROS should not introduce a highly granular permission-management interface for clinic owners in V1.

Future versions may support configurable permissions if real-world usage demonstrates a need for them.

Any future permission expansion must preserve:

* Clinic isolation
* Doctor data isolation
* Medical-record confidentiality
* Least privilege
* Backend authorization

---

# 16. Security Principle

Permissions are a security boundary, not merely a UI feature.

Every backend operation involving protected data must answer:

1. Is the user authenticated?
2. What role does the user have?
3. Which clinic does the user belong to?
4. Is the requested resource part of the user's authorized scope?
5. Is the requested action permitted for the user's role?

Only when all required conditions are satisfied should the operation proceed.
