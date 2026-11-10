# IATROS — API Specification

## 1. Purpose

This document defines the REST API contract for IATROS.

It establishes:

* API structure
* Endpoint conventions
* Resource organization
* Request/response conventions
* Authentication
* Authorization
* Validation
* Error handling
* Pagination
* Search
* Filtering
* Sorting
* Date/time handling
* API versioning
* Module boundaries

The API must support the React frontend while keeping the backend authoritative for business rules, security, and data integrity.

---

# 2. API Architecture

The IATROS frontend communicates with the backend through REST APIs.

```text
React + TypeScript
        │
        │ HTTPS / JSON
        ▼
Spring Boot REST API
        │
        ▼
Business Services
        │
        ▼
Repositories
        │
        ▼
PostgreSQL
```

The frontend must not access PostgreSQL directly.

---

# 3. API Base Path

The API should use a versioned base path:

```text
/api/v1
```

Example:

```text
GET /api/v1/patients
```

Versioning provides a controlled way to introduce future breaking changes.

---

# 4. Resource-Oriented Design

Endpoints should represent business resources rather than frontend screens.

Examples:

```text
/patients
/doctors
/appointments
/consultations
/schedules
/documents
```

Avoid endpoints that expose internal implementation details.

For example, prefer:

```text
GET /api/v1/patients/{id}
```

rather than:

```text
GET /api/v1/getPatientByDatabaseId
```

---

# 5. Authentication API

## Login

```text
POST /api/v1/auth/login
```

Request:

```json
{
  "email": "doctor@example.com",
  "password": "password"
}
```

On success (`200`), the server creates a session, sets the `IATROS_SESSION` cookie, and rotates the `XSRF-TOKEN` cookie. The response body contains the current-user payload (same as `GET /auth/me`). No token is returned in the body (`decisions.md` D-010, D-017).

Errors: `400 VALIDATION_ERROR`, `401 INVALID_CREDENTIALS` (identical for every failure reason), `429 RATE_LIMITED`.

State-changing requests, including login, must include the CSRF token from the `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header (D-011).

## CSRF Token

```text
GET /api/v1/auth/csrf
```

Public. Returns `204` and sets the `XSRF-TOKEN` cookie. The SPA calls it once at start-up. It does not create a session.

## Accept Invitation

```text
POST /api/v1/auth/accept-invitation
```

Public. Sets the password of an invited team member using a single-use invitation token (D-021) and activates the account. Same request body as Reset Password.

Returns `204`. Errors: `400 VALIDATION_ERROR` (including `confirmPassword` → `PASSWORD_MISMATCH`), `400 INVALID_TOKEN` (unknown, expired, or already used), `429 RATE_LIMITED`. The user then signs in normally.

## Change Password

```text
POST /api/v1/auth/change-password
```

Request:

```json
{
  "currentPassword": "...",
  "newPassword": "...",
  "confirmPassword": "..."
}
```

Requires an authenticated session. Returns `204` and invalidates the user's other sessions; the current session stays valid.

Errors: `400 VALIDATION_ERROR` with field codes `INCORRECT_PASSWORD` (`currentPassword`) or `PASSWORD_MISMATCH` (`confirmPassword`), `401 UNAUTHENTICATED`, `429 RATE_LIMITED`.

Passwords must be 8–128 characters (D-013).

---

## Logout

```text
POST /api/v1/auth/logout
```

Invalidates the server-side session, clears the session cookie, and returns `204`. Calling it without a session also returns `204`.

---

## Current User

```text
GET /api/v1/auth/me
```

Returns:

```json
{
  "id": "0190...",
  "firstName": "Elena",
  "lastName": "Papadopoulou",
  "email": "doctor@example.com",
  "role": "DOCTOR",
  "preferredLanguage": "fr",
  "doctorId": "0190...",
  "clinic": {
    "id": "0190...",
    "name": "Demo Clinic",
    "timeZone": "Europe/Athens",
    "defaultLanguage": "en"
  }
}
```

`preferredLanguage` is `null` when the user follows the clinic default. `doctorId` is set when the user has a doctor profile (doctors, and owners who practise, D-033).

The frontend should use this endpoint to establish the current authenticated user's context. Without a valid session it returns `401 UNAUTHENTICATED`.

---

## Update Preferences

```text
PUT /api/v1/auth/me/preferences
```

```json
{
  "preferredLanguage": "fr"
}
```

Saves the signed-in user's own preferences. `preferredLanguage` is `en`, `fr`, or `null` (follow the clinic default). Any other value returns `400 VALIDATION_ERROR` with field code `INVALID_FORMAT`. Returns `200` with the current-user payload. Requires a session and CSRF token; available to every role. Theme stays a per-device setting and is not stored on the server.

---

## Forgot Password

```text
POST /api/v1/auth/forgot-password
```

Request:

```json
{
  "email": "user@example.com"
}
```

Public. Always returns `202`, whether or not the email belongs to an active account, so the response does not reveal which emails exist. If it does, a reset link valid for 1 hour is emailed (D-017). `429 RATE_LIMITED` applies.

---

## Reset Password

```text
POST /api/v1/auth/reset-password
```

Request:

```json
{
  "token": "...",
  "newPassword": "...",
  "confirmPassword": "..."
}
```

Public. The token comes from the URL fragment of the emailed link (`/reset-password#token=...`). Returns `204` and invalidates all of the user's sessions.

Errors: `400 VALIDATION_ERROR` (including `confirmPassword` → `PASSWORD_MISMATCH`), `400 INVALID_TOKEN`, `429 RATE_LIMITED`.

---

# 6. Clinic API

## Get Current Clinic

```text
GET /api/v1/clinic
```

Any authenticated role. Always the caller's own clinic; there is no clinic ID in the URL.

```json
{
  "id": "0190a000-...",
  "name": "Example Medical Clinic",
  "email": "contact@example.test",
  "phone": "+30 210 000 0000",
  "address": "12 Example Street, Athens",
  "version": 2,
  "settings": {
    "timeZone": "Europe/Athens",
    "defaultAppointmentMinutes": 30,
    "defaultLanguage": "en",
    "weekStartDay": 1,
    "version": 5
  }
}
```

`weekStartDay` is ISO-8601 (1 = Monday … 7 = Sunday). The profile and the settings carry separate `version` values because they are edited separately.

## Update Clinic

```text
PUT /api/v1/clinic
```

Owner-only operation.

```json
{
  "name": "Example Medical Clinic",
  "email": "contact@example.test",
  "phone": "+30 210 000 0000",
  "address": "12 Example Street, Athens",
  "version": 2
}
```

`name` is required (max 200). `email` (valid address, max 254), `phone` (max 40), and `address` (max 500) are optional; blank values are stored as empty. Returns the updated clinic.

Errors: `400 VALIDATION_ERROR`, `403 FORBIDDEN` (not owner), `409 CONCURRENT_MODIFICATION` (stale `version`).

## Update Clinic Settings

```text
PUT /api/v1/clinic/settings
```

Owner-only operation.

```json
{
  "timeZone": "Europe/Athens",
  "defaultAppointmentMinutes": 30,
  "defaultLanguage": "en",
  "weekStartDay": 1,
  "version": 5
}
```

`timeZone` must be an IANA zone ID (`INVALID` otherwise). `defaultAppointmentMinutes` is 5–480, `defaultLanguage` is `en` or `fr`, `weekStartDay` is 1–7. Returns the updated clinic.

Errors: `400 VALIDATION_ERROR`, `403 FORBIDDEN`, `409 CONCURRENT_MODIFICATION`.

---

# 7. Staff API

Every staff endpoint is owner-only (`403 FORBIDDEN` for doctors and secretaries). A member ID from another clinic returns `404 STAFF_NOT_FOUND`. Staff rules are recorded in D-034.

## List Staff

```text
GET /api/v1/staff
```

| Parameter | Description |
| --- | --- |
| `search` | Case-insensitive match on full name or email (max 100 characters). `%` and `_` are literal. |
| `role` | `OWNER`, `DOCTOR`, or `SECRETARY` |
| `status` | `INVITED`, `ACTIVE`, or `DEACTIVATED` |
| `page`, `size` | Pagination (§26) |
| `sort` | `lastName`, `firstName`, `email`, `role`, `status`, `createdAt`, `lastLoginAt`. Default `lastName,asc` then `firstName,asc`. |

Example:

```text
GET /api/v1/staff?page=0&size=20&search=smith&role=DOCTOR&sort=lastName,asc
```

Each item:

```json
{
  "id": "0190a000-...",
  "firstName": "Daniel",
  "lastName": "Brown",
  "email": "daniel@example.test",
  "phone": null,
  "role": "DOCTOR",
  "status": "ACTIVE",
  "lastLoginAt": "2026-10-01T08:30:00Z",
  "createdAt": "2026-09-01T08:30:00Z",
  "version": 3,
  "doctor": {
    "id": "0190a000-...",
    "primarySpecialty": "CARDIOLOGY",
    "additionalSpecialty": null,
    "registrationNumber": null
  }
}
```

`doctor` is `null` for members without a doctor profile.

## Get Staff Member

```text
GET /api/v1/staff/{id}
```

## Create Staff Member

```text
POST /api/v1/staff
```

This is the single creation endpoint for all clinic members (D-022). When `role` is `DOCTOR`, the request includes the doctor profile, and both are created in one transaction. The new member is `INVITED` and receives an invitation email (D-021).

```json
{
  "firstName": "Daniel",
  "lastName": "Brown",
  "email": "daniel@example.test",
  "phone": null,
  "role": "DOCTOR",
  "doctor": {
    "primarySpecialty": "CARDIOLOGY",
    "additionalSpecialty": "Interventional cardiology",
    "registrationNumber": "10003456789"
  }
}
```

Allowed roles: `DOCTOR`, `SECRETARY`. Owners are not created through this endpoint (D-020). `doctor` is required for `DOCTOR` and must be absent otherwise; `primarySpecialty` is a code from `GET /api/v1/specialties`. Returns `201` with the member.

Errors: `400 VALIDATION_ERROR` (`role` → `INVALID` for `OWNER`; `doctor` → `REQUIRED` / `INVALID`; `doctor.primarySpecialty` → `INVALID` for an unknown code), `409 EMAIL_ALREADY_USED`.

## Update Staff Member

```text
PUT /api/v1/staff/{id}
```

```json
{
  "firstName": "Daniel",
  "lastName": "Brown",
  "email": "daniel@example.test",
  "phone": "+30 210 000 0000",
  "version": 3
}
```

Contact details only. The role cannot be changed in V1, and doctor profiles are edited through the doctor API. The email can only change while the member is `INVITED`; changing it re-sends the invitation to the new address.

Errors: `400 VALIDATION_ERROR`, `409 EMAIL_ALREADY_USED`, `409 CONCURRENT_MODIFICATION`, `422 EMAIL_CHANGE_NOT_ALLOWED`.

## Deactivate / Reactivate Staff Member

```text
PATCH /api/v1/staff/{id}/status
```

```json
{ "status": "DEACTIVATED" }
```

`status` is `ACTIVE` or `DEACTIVATED`; setting the current status is a no-op. Deactivation immediately ends the member's sessions and revokes their open invitation and reset links. Reactivating a member who never set a password returns them to `INVITED`.

Errors: `400 VALIDATION_ERROR` (`status` → `INVALID` for `INVITED`), `422 OWNER_STATUS_LOCKED`, `422 STAFF_HAS_UPCOMING_APPOINTMENTS` (a doctor with scheduled, confirmed, waiting, or in-progress appointments that have not ended).

## Resend Invitation

```text
POST /api/v1/staff/{id}/invitation
```

Issues a new invitation link (previous links stop working) and returns `202`. Limited to 5 per member per hour.

Errors: `422 USER_NOT_INVITED`, `429 RATE_LIMITED`.

---

# 8. Doctor API

Doctors are staff members with a doctor-specific profile. `{id}` is the **doctor id** (used by schedules and appointments), not the staff user id.

Scope: owners and secretaries see every doctor of their clinic (booking needs them); a doctor sees only themselves. Anything else is `404 DOCTOR_NOT_FOUND`.

## List Doctors

```text
GET /api/v1/doctors?search=&specialty=CARDIOLOGY&status=ACTIVE&page=0&size=20&sort=lastName,asc
```

* `search` — name or email, case-insensitive.
* `specialty` — a specialty code; an unknown code is `400` with field `specialty` → `INVALID`.
* `status` — `INVITED`, `ACTIVE`, `DEACTIVATED`.
* Sort fields: `lastName` (default, then `firstName`), `firstName`, `status`.

```json
{
  "id": "…",
  "userId": "…",
  "firstName": "Daniel",
  "lastName": "Brown",
  "email": "daniel@example.com",
  "phone": null,
  "role": "DOCTOR",
  "status": "ACTIVE",
  "primarySpecialty": "CARDIOLOGY",
  "additionalSpecialty": null,
  "registrationNumber": "10003456789",
  "version": 4
}
```

`role` is `OWNER` for an owner who is also a doctor (D-033).

## Get Doctor

```text
GET /api/v1/doctors/{id}
```

Doctors are created through `POST /api/v1/staff` (D-022). There is no separate doctor-creation endpoint. Contact details are edited through the staff API.

## Update Doctor Profile

```text
PUT /api/v1/doctors/{id}
```

```json
{ "primarySpecialty": "CARDIOLOGY", "additionalSpecialty": null, "registrationNumber": "10003456789", "version": 4 }
```

Owners can update any clinic doctor; doctors only themselves; secretaries get `403`. An unknown or inactive specialty is `400` with field `primarySpecialty` → `INVALID`; a stale `version` is `409 CONCURRENT_MODIFICATION`. Audited as `DOCTOR_PROFILE_UPDATED`.

A doctor's patients are listed with `GET /api/v1/patients?primaryDoctorId={id}` (§10), which applies the caller's patient scope; there is no separate `/doctors/{id}/patients` endpoint.

---

# 9. Specialty API

## List Specialties

```text
GET /api/v1/specialties
```

Any authenticated role. Returns the active specialties, unpaginated (the catalog is small and fixed):

```json
[{ "id": "0190a000-...", "code": "CARDIOLOGY" }]
```

`code` is the stable identifier; the frontend translates it for display.

Specialties should be represented as structured data for primary specialty selection.

Doctors may additionally have free-text additional specialty information.

The initial specialty list may be managed as controlled application data rather than exposing full specialty administration to users.

---

# 10. Patient API

Scope (D-029, D-036): owners and secretaries work on every patient of their clinic; a doctor only on patients they are the primary or an authorized doctor for. Anything else is `404 PATIENT_NOT_FOUND`, so the existence of other patients is not revealed. Archiving and doctor access are owner-only (`403 FORBIDDEN` for other roles).

## List Patients

```text
GET /api/v1/patients?search=&primaryDoctorId=&status=ACTIVE&page=0&size=20&sort=lastName,asc
```

* `search` — name, phone, or email, case-insensitive (trigram-indexed `search_text`).
* `primaryDoctorId` — patients whose primary doctor is this doctor.
* `status` — `ACTIVE` (default) or `ARCHIVED`; archived patients are hidden unless requested.
* Sort fields: `lastName` (default, then `firstName`), `firstName`, `dateOfBirth`, `createdAt`.

The query itself is restricted to the caller's scope; nothing is filtered after loading.

```json
{
  "id": "…",
  "firstName": "Alice",
  "lastName": "Martin",
  "dateOfBirth": "1980-05-01",
  "gender": "FEMALE",
  "phone": "+33 6 00 00 00 00",
  "email": null,
  "status": "ACTIVE",
  "primaryDoctor": { "id": "…", "firstName": "Daniel", "lastName": "Brown", "status": "ACTIVE", "relationshipType": "PRIMARY" },
  "nextAppointmentAt": "2027-04-12T07:00:00Z",
  "lastVisitAt": "2027-03-02T08:00:00Z"
}
```

`gender` is `FEMALE`, `MALE`, `OTHER`, `UNDISCLOSED`, or `null`. `nextAppointmentAt` is the start of the earliest upcoming `SCHEDULED`, `CONFIRMED`, or `WAITING` appointment; `lastVisitAt` the start of the latest `COMPLETED` one. Both are computed within the caller's appointment scope (a doctor sees only their own appointments) in one batched query per page, and are `null` when there is none.

## Get Patient

```text
GET /api/v1/patients/{id}
```

The summary fields plus `address`, `emergencyContactName`, `emergencyContactPhone`, `archivedAt`, `createdAt`, `version`, and `doctors`: every doctor with access, primary first. `doctors` is `null` for secretaries, who only see `primaryDoctor` (D-029). There is no separate `GET /patients/{id}/doctors`.

## Create Patient

```text
POST /api/v1/patients
```

```json
{
  "firstName": "Alice",
  "lastName": "Martin",
  "dateOfBirth": "1980-05-01",
  "gender": null,
  "phone": "+33 6 00 00 00 00",
  "email": null,
  "address": null,
  "emergencyContactName": null,
  "emergencyContactPhone": null,
  "primaryDoctorId": "…"
}
```

Any role. Returns `201` with the patient. The patient, their medical record, and the primary-doctor link are created together. A doctor can only name themselves as primary doctor (`403 FORBIDDEN` otherwise). Audited as `PATIENT_CREATED`.

Errors: `400 VALIDATION_ERROR` — `dateOfBirth` → `DATE_IN_FUTURE` (after today in the clinic's time zone) or `OUT_OF_RANGE` (before 1900); `primaryDoctorId` → `INVALID` (not a doctor of the clinic) or `DOCTOR_DEACTIVATED`.

## Update Patient

```text
PUT /api/v1/patients/{id}
```

The create body without `primaryDoctorId`, plus `version`. Any role within its scope. Archived patients are read-only. Audited as `PATIENT_UPDATED`.

Errors: `400 VALIDATION_ERROR`, `409 CONCURRENT_MODIFICATION`, `422 PATIENT_ARCHIVED`.

## Archive / Restore Patient

```text
PATCH /api/v1/patients/{id}/status
```

```json
{ "status": "ARCHIVED" }
```

Owner only. Setting the current status is a no-op. Archiving keeps all history (D-028). Audited as `PATIENT_ARCHIVED` / `PATIENT_RESTORED`.

Errors: `422 PATIENT_HAS_UPCOMING_APPOINTMENTS` (scheduled, confirmed, waiting, or in-progress appointments that have not ended).

## Change Primary Doctor

```text
PATCH /api/v1/patients/{id}/primary-doctor
```

```json
{ "doctorId": "…" }
```

Owner only; returns the patient. The previous primary doctor stays as an authorized doctor so they keep access to a patient they treated; the owner can remove them afterwards. Choosing the current primary doctor is a no-op. Audited as `PATIENT_PRIMARY_DOCTOR_CHANGED`.

Errors: `400` (`doctorId` → `INVALID` / `DOCTOR_DEACTIVATED`), `422 PATIENT_ARCHIVED`.

## Manage Authorized Doctors

```text
POST /api/v1/patients/{id}/doctors
```

```json
{ "doctorId": "…" }
```

Owner only; returns the patient. Audited as `PATIENT_DOCTOR_AUTHORIZED`.

Errors: `400` (`doctorId` → `INVALID` / `DOCTOR_DEACTIVATED`), `409 DOCTOR_ALREADY_AUTHORIZED` (already primary or authorized), `422 PATIENT_ARCHIVED`.

```text
DELETE /api/v1/patients/{id}/doctors/{doctorId}
```

Owner only; returns `204`. Removing access never deletes anything from the medical record. Audited as `PATIENT_DOCTOR_REMOVED`.

Errors: `404 DOCTOR_NOT_FOUND` (not linked), `422 PRIMARY_DOCTOR_REQUIRED` (assign another primary doctor first), `422 PATIENT_ARCHIVED`.

---

# 11. Patient Profile API

The patient profile can aggregate information from several modules.

```text
GET /api/v1/patients/{id}/profile
```

The response may contain summary information such as:

* Patient information
* Primary doctor
* Authorized doctors
* Medical-record summary
* Upcoming appointments
* Recent consultations
* Recent documents

The endpoint must not bypass the permissions of any underlying resource.

---

# 12. Medical Record API

Every patient has one medical record, created with the patient. Scope (D-037): the owner for every clinic patient, doctors for patients they are primary or authorized for. Secretaries get `403 FORBIDDEN`; anyone else, or another clinic's patient, gets `404 PATIENT_NOT_FOUND`. These rules apply to every endpoint in §12–16.

Archived patients' records are read-only: every write returns `422 PATIENT_ARCHIVED`.

## Get Medical Record

```text
GET /api/v1/patients/{patientId}/medical-record
```

```json
{
  "patientId": "…",
  "allergies": [{ "id": "…", "allergen": "Penicillin", "reaction": "Hives", "severity": "SEVERE", "notes": null, "updatedAt": "…", "version": 0 }],
  "conditions": [{ "id": "…", "name": "Hypertension", "description": null, "status": "ACTIVE", "diagnosedOn": "2020-03-01", "updatedAt": "…", "version": 0 }],
  "medications": [{ "id": "…", "name": "Metformin", "dosage": "500 mg", "frequency": "Twice daily", "notes": null, "startDate": "2024-01-01", "endDate": null, "active": true, "updatedAt": "…", "version": 0 }],
  "history": [{ "id": "…", "description": "Appendectomy", "occurredOn": "2001-05-01", "notes": null, "updatedAt": "…", "version": 0 }]
}
```

Sections are returned whole, in reading order: allergies by name; conditions active first, then by name; medications current first, then by name; history most recent first, undated entries last. General notes and consultations are paginated separately (§12 Notes, §16).

The record has no fields of its own, so there is no `PUT /medical-record`; each section is maintained through its own endpoints.

## Structured Sections

```text
POST   /api/v1/patients/{patientId}/medical-record/{section}
PUT    /api/v1/patients/{patientId}/medical-record/{section}/{entryId}
DELETE /api/v1/patients/{patientId}/medical-record/{section}/{entryId}
```

`{section}` is `allergies`, `conditions`, `medications`, or `history` (bodies in §13–15). Anyone with clinical access may add, edit, or remove entries. `POST` returns `201` with the entry; `PUT` requires `version` and returns the entry; `DELETE` returns `204` and soft-deletes it (D-030), so it disappears for every reader. An entry ID that does not belong to this patient's record is `404` (`ALLERGY_NOT_FOUND`, `MEDICAL_CONDITION_NOT_FOUND`, `MEDICATION_NOT_FOUND`, `MEDICAL_HISTORY_ENTRY_NOT_FOUND`). Audited as `MEDICAL_ENTRY_CREATED`, `MEDICAL_ENTRY_UPDATED`, and `MEDICAL_ENTRY_DELETED` with the `patientId` only.

Errors: `400 VALIDATION_ERROR` (including `version` → `REQUIRED` on update), `409 CONCURRENT_MODIFICATION`, `422 PATIENT_ARCHIVED`.

## General Notes

```text
GET    /api/v1/patients/{patientId}/medical-record/notes?page=0&size=20
POST   /api/v1/patients/{patientId}/medical-record/notes
PUT    /api/v1/patients/{patientId}/medical-record/notes/{noteId}
DELETE /api/v1/patients/{patientId}/medical-record/notes/{noteId}
```

Newest first (`sort=createdAt,desc` is the default and only sort field). Body: `{ "content": "…", "version": 0 }` (`content` required, at most 20,000 characters; `version` on update only).

```json
{
  "id": "…",
  "content": "Patient reports better sleep.",
  "author": { "id": "…", "firstName": "Daniel", "lastName": "Brown" },
  "createdAt": "…",
  "updatedAt": "…",
  "edited": false,
  "editable": true,
  "version": 0
}
```

Anyone with clinical access adds notes; only the author edits or removes them (`403 FORBIDDEN`). `editable` tells the caller whether they are the author. Unknown notes are `404 MEDICAL_NOTE_NOT_FOUND`.

---

# 13. Medical Conditions API

```json
{ "name": "Hypertension", "description": null, "status": "ACTIVE", "diagnosedOn": "2020-03-01", "version": 0 }
```

`name` is required (at most 200 characters), `status` is `ACTIVE` or `RESOLVED`, and `description` is at most 4,000 characters. `diagnosedOn` is optional and cannot be after today in the clinic's time zone (`DATE_IN_FUTURE`).

Past medical history entries (`history`) work the same way: `{ "description": "…", "occurredOn": "2001-05-01", "notes": null }`. `description` is required; `occurredOn` is optional and cannot be in the future.

---

# 14. Allergies API

```json
{ "allergen": "Penicillin", "reaction": "Hives", "severity": "SEVERE", "notes": null, "version": 0 }
```

`allergen` is required (at most 200 characters). `severity` is `MILD`, `MODERATE`, `SEVERE`, `UNKNOWN`, or `null`.

---

# 15. Medications API

```json
{ "name": "Metformin", "dosage": "500 mg", "frequency": "Twice daily", "notes": null, "startDate": "2024-01-01", "endDate": null, "active": true, "version": 0 }
```

`name` and `active` are required. An `endDate` before `startDate` is `END_BEFORE_START` on `endDate`.

The V1 system does not implement a full electronic prescribing system.

---

# 16. Consultation API

Scope as in §12. Every doctor linked to the patient, and the owner, reads all of the patient's consultations (D-037).

## List Consultations

```text
GET /api/v1/patients/{patientId}/consultations?doctorId=&from=2026-01-01&to=2026-12-31&page=0&size=20
```

Newest first (`consultedAt`). `doctorId` filters by doctor; `from` and `to` are clinic-local dates, both inclusive (`to` before `from` is `END_BEFORE_START` on `to`). Each item is a summary: `id`, `consultedAt`, `doctor` (`id`, `firstName`, `lastName`), `reason`, `diagnosis`, `followUpOn`.

## Get Consultation

```text
GET /api/v1/consultations/{id}
```

The summary fields plus `patientId`, `clinicalNotes`, `treatmentNotes`, `followUpNotes`, `appointmentId`, `createdAt`, `updatedAt`, `editable` (true for the consultation's doctor), and `version`. A consultation of a patient outside the caller's scope is `404 CONSULTATION_NOT_FOUND`.

## Create Consultation

```text
POST /api/v1/patients/{patientId}/consultations
```

```json
{
  "consultedAt": "2026-10-05T07:30:00Z",
  "reason": "Follow-up",
  "diagnosis": "…",
  "clinicalNotes": "…",
  "treatmentNotes": "…",
  "followUpNotes": "…",
  "followUpOn": "2026-11-05",
  "appointmentId": null
}
```

The doctor is always the caller's own doctor profile. There is no `doctorId` field, and an owner without a doctor profile gets `403 FORBIDDEN`. `consultedAt` and `reason` are required. Returns `201`; audited as `CONSULTATION_CREATED`.

`appointmentId` (optional, creation only; ignored on update) links the consultation to the appointment it records. The appointment must belong to the same clinic and patient, be the caller's own, and have a blocking status (D-024); otherwise `400 VALIDATION_ERROR` with `appointmentId` → `INVALID`. An appointment already linked to a consultation is `409 CONSULTATION_ALREADY_EXISTS`.

## Update Consultation

```text
PUT /api/v1/consultations/{id}
```

The create body plus `version`. Only the consultation's doctor may edit it (`403 FORBIDDEN`); consultations are never deleted. Audited as `CONSULTATION_UPDATED` with the names of the changed fields, never their content.

Errors: `400 VALIDATION_ERROR` — `consultedAt` → `DATE_IN_FUTURE` (more than five minutes ahead); `followUpOn` → `BEFORE_CONSULTATION` (before the consultation's clinic-local date). Also `409 CONCURRENT_MODIFICATION` and `422 PATIENT_ARCHIVED`.

---

# 17. Appointment API

Rules are in D-024, D-027, and D-038. Every role reads appointments within its data scope: owners and secretaries the whole clinic, a doctor only their own. Another doctor's appointment or another clinic's is `404 APPOINTMENT_NOT_FOUND`.

## List Appointments

```text
GET /api/v1/appointments?patientId=...&from=2027-04-01&to=2027-04-30&page=0&size=20
```

Filters: `doctorId`, `patientId`, `status` (repeatable), `type` (repeatable), `from` and `to` (clinic-local dates, inclusive; `to` before `from` → `400` with `to` → `END_BEFORE_START`). Sort: `startAt` (default `startAt,desc`). Paginated (§26). Items are summaries:

```json
{
  "id": "…",
  "startAt": "2027-04-12T07:00:00Z",
  "endAt": "2027-04-12T07:30:00Z",
  "patient": { "id": "…", "firstName": "Alice", "lastName": "Martin" },
  "doctor": { "id": "…", "firstName": "Daniel", "lastName": "Brown" },
  "type": "FOLLOW_UP",
  "status": "SCHEDULED",
  "reason": "Knee pain"
}
```

## Get Appointment

```text
GET /api/v1/appointments/{id}
```

The summary fields plus `notes`, `cancellationReason`, `statusChangedAt`, `createdBy` (name reference), `createdAt`, `updatedAt`, `version`, and what the caller may do, decided by the server:

* `editable` — details and time can change (status `SCHEDULED` or `CONFIRMED`, patient not archived).
* `cancellable` — the appointment can be cancelled.
* `nextStatuses` — the status changes the caller may make now (cancellation excluded).
* `consultationId` — the linked consultation; always `null` for secretaries.

## Create Appointment

```text
POST /api/v1/appointments
```

```json
{
  "patientId": "…",
  "doctorId": "…",
  "startAt": "2027-04-12T07:00:00Z",
  "endAt": "2027-04-12T07:30:00Z",
  "type": "FOLLOW_UP",
  "reason": "Knee pain",
  "notes": null
}
```

Everything is required except `reason` (≤ 500) and `notes` (≤ 4,000). Who may book whom follows D-027; the patient must be active (`422 PATIENT_ARCHIVED`) and the doctor not deactivated. Checks, in order:

1. `endAt` after `startAt` (`422 INVALID_TIME_RANGE`) and at most 12 hours (`422 APPOINTMENT_TOO_LONG`).
2. Not in the past, with five minutes of clock skew (`400` with `startAt` → `DATE_IN_PAST`).
3. Inside one of the doctor's working ranges for that date, schedule exceptions included (`422 DOCTOR_UNAVAILABLE`).
4. No overlap with the doctor's blocking appointments (`409 APPOINTMENT_CONFLICT`, also enforced by the database, D-005).

Returns `201` with the full appointment; audited as `APPOINTMENT_CREATED`.

## Update Appointment

```text
PUT /api/v1/appointments/{id}
```

`type`, `reason`, `notes`, and `version`. Only while editable (`422 APPOINTMENT_NOT_EDITABLE`; archived patient → `422 PATIENT_ARCHIVED`). Audited as `APPOINTMENT_UPDATED` with the names of the changed fields.

## Reschedule Appointment

```text
PATCH /api/v1/appointments/{id}/schedule
```

`doctorId`, `startAt`, `endAt`, and `version`. Only while editable. A new doctor must be bookable for the patient by the caller (D-027). The booking checks above run again, ignoring the appointment itself. Audited as `APPOINTMENT_RESCHEDULED`.

## Change Appointment Status

```text
PATCH /api/v1/appointments/{id}/status
```

`status` and `version`. Transitions follow D-024; an invalid one, or `CANCELLED`, is `422 INVALID_STATUS_TRANSITION` (cancel through the endpoint below). `IN_CONSULTATION` and `COMPLETED` are set only by the owner or the appointment's doctor (`403 FORBIDDEN`). `WAITING`, `IN_CONSULTATION`, `COMPLETED`, and `NO_SHOW` cannot be set before the appointment's clinic-local date (`422 APPOINTMENT_NOT_STARTED`). Allowed on archived patients' appointments. Audited as `APPOINTMENT_STATUS_CHANGED` with the previous and new status.

## Cancel Appointment

```text
POST /api/v1/appointments/{id}/cancel
```

Optional `reason` (≤ 500) and `version`. Allowed from `SCHEDULED`, `CONFIRMED`, and `WAITING` (D-024), also for archived patients. Cancellation keeps the appointment and its history and frees the time. Audited as `APPOINTMENT_CANCELLED`.

## Appointment Times

Appointment requests and responses use `startAt` and `endAt` as ISO-8601 instants (D-004). Appointment types use the stable values in D-023. Every write takes the loaded `version` (`409 CONCURRENT_MODIFICATION` when stale).

---

# 18. Appointment Availability API

The frontend needs a way to determine available booking times.

```text
GET /api/v1/appointments/availability
```

Possible parameters:

```text
doctorId
date
duration
```

Example:

```text
GET /api/v1/appointments/availability?doctorId=...&date=2027-04-12&duration=30
```

The backend calculates availability based on:

* Doctor schedule
* Schedule exceptions
* Existing appointments
* Appointment duration
* Clinic rules

The frontend should not independently calculate authoritative availability.

`doctorId` and `date` are required; `duration` defaults to 30 minutes and must be 5–720 (`400` with `duration` → `OUT_OF_RANGE`). The doctor follows the calendar scope (§19). Start times are offered every 15 minutes inside each working range (`WorkingHoursService`, D-035), skipping past times and overlaps with blocking appointments:

```json
{
  "timeZone": "Europe/Athens",
  "date": "2027-04-12",
  "durationMinutes": 30,
  "slots": [{ "startAt": "2027-04-12T06:00:00Z", "endAt": "2027-04-12T06:30:00Z" }]
}
```

Slots are suggestions; booking re-checks everything, so a slot taken in the meantime is `409 APPOINTMENT_CONFLICT`.

---

# 19. Calendar API

The calendar reads appointments through a dedicated, compact, non-paginated endpoint. There is no separate calendar data model.

```text
GET /api/v1/calendar/events?from=2027-04-12&to=2027-04-18&doctorId=...
```

Parameters:

* `from`, `to` (required, dates in the clinic's time zone; maximum range 42 days)
* `doctorId` (optional, repeatable)
* `status` (optional, repeatable)

The response contains compact events plus each shown doctor's effective working hours per date (schedule with exceptions applied), so the calendar shades unavailable time in a single request:

```json
{
  "timeZone": "Europe/Athens",
  "from": "2027-04-12",
  "to": "2027-04-18",
  "events": [
    {
      "id": "…",
      "startAt": "2027-04-12T07:00:00Z",
      "endAt": "2027-04-12T07:30:00Z",
      "doctorId": "…",
      "patient": { "id": "…", "firstName": "Alice", "lastName": "Martin" },
      "type": "FOLLOW_UP",
      "status": "SCHEDULED"
    }
  ],
  "doctors": [
    {
      "id": "…",
      "firstName": "Daniel",
      "lastName": "Brown",
      "days": [{ "date": "2027-04-12", "ranges": [{ "start": "09:00", "end": "17:00" }] }]
    }
  ]
}
```

Scope: a doctor always gets only their own calendar (requesting another doctor is `404 DOCTOR_NOT_FOUND`); owners and secretaries get every clinic doctor or the requested ones. Deactivated doctors are shown only when they have events in the range or are requested explicitly. A range longer than 42 days is `400` with `to` → `OUT_OF_RANGE`; `to` before `from` is `END_BEFORE_START`. Cancelled appointments are included; the calendar hides them unless asked.

`GET /api/v1/appointments` remains the paginated list for history and reporting views.

---

# 20. Doctor Schedule API

Rules are in D-035. Reading: owners and secretaries for any clinic doctor, a doctor for themselves. Changing: owners for any clinic doctor, a doctor for themselves; secretaries get `403`. Another doctor or another clinic's doctor is `404 DOCTOR_NOT_FOUND`.

Times are `"HH:mm"` wall-clock times in the clinic's time zone (D-004); dates are `YYYY-MM-DD`. `dayOfWeek` is ISO: `1` = Monday … `7` = Sunday.

## Get Schedule

```text
GET /api/v1/doctors/{doctorId}/schedule
```

```json
{
  "doctorId": "…",
  "timeZone": "Europe/Paris",
  "version": 3,
  "periods": [
    { "dayOfWeek": 1, "startTime": "09:00", "endTime": "13:00" },
    { "dayOfWeek": 1, "startTime": "14:00", "endTime": "18:00" }
  ]
}
```

Periods are sorted by day, then start time.

## Replace Schedule

```text
PUT /api/v1/doctors/{doctorId}/schedule
```

```json
{ "periods": [{ "dayOfWeek": 1, "startTime": "09:00", "endTime": "13:00" }], "version": 3 }
```

Replaces the whole week atomically (at most 70 periods) and returns the new schedule. There are no per-period endpoints: the editor always saves a whole week, which keeps validation and concurrency simple.

Validation errors are reported for every invalid period at once, by index in the request:

* `periods[i].endTime` → `END_BEFORE_START` (a period cannot end at or before its start; periods do not cross midnight).
* `periods[i].startTime` → `OVERLAP` (periods of the same day may touch, e.g. 09:00–12:00 and 12:00–13:00, but not overlap).
* `periods[i].dayOfWeek` → `OUT_OF_RANGE`.

A stale `version` is `409 CONCURRENT_MODIFICATION`. Existing appointments are not changed. Audited as `SCHEDULE_UPDATED`.

## Schedule Exceptions

Schedule exceptions are part of V1 (D-026). Types: `UNAVAILABLE`, `EXTRA_AVAILABILITY`.

```text
GET /api/v1/doctors/{doctorId}/schedule/exceptions?from=2027-04-01&to=2027-06-30
```

`from` defaults to today in the clinic's time zone and `to` to one year later; the range may not exceed 366 days. Sorted by date, then start time.

```text
POST /api/v1/doctors/{doctorId}/schedule/exceptions
```

```json
{ "date": "2027-04-12", "type": "UNAVAILABLE", "startTime": null, "endTime": null, "reason": "Conference" }
```

* Both times null: the whole day (only for `UNAVAILABLE`).
* `EXTRA_AVAILABILITY`, or either time given: both times are required (`REQUIRED`), and `endTime` must be after `startTime` (`END_BEFORE_START`).
* `date` cannot be before today in the clinic's time zone (`DATE_IN_PAST`).
* `reason` is optional (max 500 characters) and visible to the clinic team; it is not a medical note.

Returns `201` with the exception (`id`, `date`, `type`, `startTime`, `endTime`, `reason`, `createdAt`). Audited as `SCHEDULE_EXCEPTION_CREATED`.

```text
DELETE /api/v1/doctors/{doctorId}/schedule/exceptions/{id}
```

Returns `204`; an unknown id, or one belonging to another doctor, is `404 SCHEDULE_EXCEPTION_NOT_FOUND`. Audited as `SCHEDULE_EXCEPTION_DELETED`.

## Working Hours

```text
GET /api/v1/doctors/{doctorId}/working-hours?from=2027-04-12&to=2027-04-25
```

The doctor's effective hours for each date: the weekly periods of that weekday plus extra availability, minus unavailability (D-035). This is the server's single source of availability; booking validation (Phase 9) uses the same calculation.

```json
{
  "timeZone": "Europe/Paris",
  "days": [
    { "date": "2027-04-12", "ranges": [{ "start": "09:00", "end": "13:00" }, { "start": "14:00", "end": "18:00" }] },
    { "date": "2027-04-13", "ranges": [] }
  ]
}
```

`from` and `to` are required; `to` before `from` is `END_BEFORE_START` and a range over 42 days is `OUT_OF_RANGE` (both on field `to`). A deactivated doctor has no working hours.

---

# 21. Document API

Rules are in D-044. Owners and the patient's linked doctors; secretaries receive `403`. A patient the caller may not see is `404 PATIENT_NOT_FOUND`; a document that is unknown, archived, or of such a patient is `404 DOCUMENT_NOT_FOUND`.

Document:

```json
{
  "id": "uuid",
  "patientId": "uuid",
  "consultationId": null,
  "fileName": "blood-test.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 1450000,
  "uploadedBy": { "id": "uuid", "firstName": "Elena", "lastName": "Markou" },
  "uploadedAt": "2026-10-10T08:15:00Z",
  "archivable": true
}
```

`archivable` tells the interface whether the caller may archive it (the uploader or the owner). The storage key and checksum are never returned.

## List Patient Documents

```text
GET /api/v1/patients/{patientId}/documents?consultationId=&search=&page=0&size=20&sort=uploadedAt,desc
```

Archived documents are excluded. `consultationId` narrows to one consultation's attachments; `search` matches the file name (case-insensitive contains, §25). Sort fields: `uploadedAt` (default, newest first), `fileName`.

## Get Document Metadata

```text
GET /api/v1/documents/{id}
```

## Upload Document

```text
POST /api/v1/patients/{patientId}/documents
Content-Type: multipart/form-data
```

Parts: `file` (required) and `consultationId` (optional, a consultation of the same patient). Returns `201` with the document. Field errors on `file`: `REQUIRED`, `FILE_EMPTY`, `FILE_TOO_LARGE` (over 20 MB), `UNSUPPORTED_FILE_TYPE` (not PDF, JPEG, PNG, or WebP), `FILE_TYPE_MISMATCH` (the extension does not match the content); on `consultationId`: `INVALID`. A request over the multipart limit is `413 FILE_TOO_LARGE`. An archived patient is `422 PATIENT_ARCHIVED`. Audited as `DOCUMENT_UPLOADED`.

## Download/View Document

```text
GET /api/v1/documents/{id}/content?download=false
```

Returns the bytes with the verified content type, `Content-Length`, and `Content-Disposition` (`inline`, or `attachment` with `download=true`) carrying the UTF-8 file name. Responses are not cacheable and carry `X-Content-Type-Options: nosniff`. Authorization is checked before the file is opened, and each call is audited as `DOCUMENT_VIEWED`. The interface links to this URL directly; the session cookie authorizes it.

## Archive Document

```text
DELETE /api/v1/documents/{id}
```

Archives the document (`204`); the file is kept. Only the uploader or the owner (`403` otherwise); not on archived patients (`422 PATIENT_ARCHIVED`). Audited as `DOCUMENT_ARCHIVED`.

---

# 22. Dashboard API

Dashboard endpoints should provide aggregated information rather than requiring the frontend to make a large number of unrelated requests.

Rules: decisions.md D-039. Both dashboards describe "today" in the clinic's time zone and include `timeZone` and `today` (`YYYY-MM-DD`).

## Owner Dashboard

```text
GET /api/v1/dashboard/owner
```

Owner-only. Response:

```json
{
  "timeZone": "Europe/Paris",
  "today": "2026-10-10",
  "summary": { "appointments": 12, "patients": 11, "remaining": 5, "waiting": 1, "inConsultation": 1, "completed": 4, "noShows": 1, "cancelled": 0 },
  "activePatients": 240,
  "activeDoctors": 3,
  "doctors": [{ "doctor": { "id": "uuid", "firstName": "Daniel", "lastName": "Moreau" }, "appointments": 6, "completed": 2, "inProgress": 1, "remaining": 3, "noShows": 0 }],
  "schedule": [],
  "scheduleTruncated": false,
  "recentActivity": []
}
```

`summary.patients` counts distinct patients; cancelled appointments are counted only in `cancelled`. `activePatients` here is the number of patients with status `ACTIVE`. `schedule` holds at most 100 of today's appointments, ordered by start (`scheduleTruncated` when more exist). A schedule item is `{ appointmentId, startAt, endAt, status, type, patient, doctor, alerts }`; `alerts` is always `null` on this dashboard. `recentActivity` holds the latest 8 activity items (§24).

## Doctor Dashboard

```text
GET /api/v1/dashboard/doctor
```

Doctors, and owners with a doctor profile; always the caller's own data (no `doctorId` parameter). Response: `timeZone`, `today`, `doctor`, `summary` (as above, for the doctor's day), `upcomingAppointments` (`SCHEDULED`/`CONFIRMED` in the next 7 days, today excluded), `patients` and `primaryPatients` (active linked patients), `schedule` (today's appointments; `alerts` is `{ severeAllergies: [names], activeConditions: [names] }` for linked patients, `null` otherwise), `recentPatients` (up to 5 `{ patient, lastVisitAt }` from completed appointments with linked patients), and `recentActivity` (latest 8 of the doctor's own activity).

An owner without a doctor profile, or a secretary, receives `403 FORBIDDEN`.

The frontend must not request another user's dashboard.

---

# 23. KPI API

Both endpoints accept optional `from` and `to` (inclusive clinic-local dates). Default: the last 30 days ending today. Errors: `400 VALIDATION_ERROR` with `END_BEFORE_START`, or `OUT_OF_RANGE` above 366 days. Definitions: decisions.md D-031 and D-039.

```json
{
  "from": "2026-09-11",
  "to": "2026-10-10",
  "previousFrom": "2026-08-12",
  "previousTo": "2026-09-10",
  "doctor": null,
  "current": {
    "appointments": 120,
    "appointmentsByStatus": { "COMPLETED": 90, "NO_SHOW": 6, "CANCELLED": 10, "SCHEDULED": 14 },
    "completionRate": 0.849,
    "noShowRate": 0.0625,
    "cancellationRate": 0.083,
    "newPatients": 14,
    "activePatients": 230,
    "consultations": 88
  },
  "previous": { },
  "trendBucket": "DAY",
  "trend": [{ "date": "2026-09-11", "appointments": 4, "completed": 3, "cancelled": 1, "noShows": 0 }],
  "doctors": []
}
```

Rates are fractions between 0 and 1, or `null` when the denominator is 0. `trendBucket` is `DAY` (periods up to 62 days) or `MONTH`; a monthly bucket's `date` is the first day of the month.

## Clinic KPIs

```text
GET /api/v1/kpis/clinic
```

Owner-only. `doctor` is `null`; `doctors` lists `{ doctor, appointments, completed, cancelled, noShows, completionRate, consultations }` per doctor, sorted by name.

## Doctor KPIs

```text
GET /api/v1/kpis/doctors/{doctorId}
```

A doctor may access only their own KPI data; another doctor's ID returns `404 DOCTOR_NOT_FOUND`. The owner may access any clinic doctor's KPIs. `doctor` is set and `doctors` is empty. Secretaries receive `403 FORBIDDEN`.

---

# 24. Activity API

## Clinic Activity

```text
GET /api/v1/activity
```

Owners and doctors; secretaries receive `403 FORBIDDEN`. Always newest first.

Query parameters:

| Parameter      | Meaning                                                                                    |
| -------------- | ------------------------------------------------------------------------------------------ |
| `from`, `to`   | Inclusive clinic-local dates (optional; `END_BEFORE_START` when reversed)                  |
| `type`         | Repeatable: `APPOINTMENTS`, `PATIENTS`, `CLINICAL`, `SCHEDULES`, `TEAM`, `CLINIC`; all when omitted |
| `doctorId`     | Owner: narrow to one doctor. Doctor: only their own ID (another is `404 DOCTOR_NOT_FOUND`) |
| `page`, `size` | Pagination (§26)                                                                           |

Item:

```json
{
  "id": "uuid",
  "occurredAt": "2026-10-10T08:15:00Z",
  "action": "APPOINTMENT_STATUS_CHANGED",
  "category": "APPOINTMENTS",
  "actor": { "id": "uuid", "firstName": "Sara", "lastName": "Lambert" },
  "entityType": "APPOINTMENT",
  "patient": { "id": "uuid", "firstName": "Alice", "lastName": "Martin" },
  "doctor": { "id": "uuid", "firstName": "Daniel", "lastName": "Moreau" },
  "staff": null,
  "appointmentId": "uuid",
  "status": "COMPLETED"
}
```

The endpoint returns only activity the requesting user is authorized to view (D-039): doctors see their own activity, and `patient` is `null` for patients the caller may not see. Security events and document views are never included, and items never contain clinical content.

---

# 25. Search API Design

IATROS should generally prefer resource-specific search rather than one unrestricted global search endpoint.

Examples:

```text
GET /api/v1/patients?search=john
```

```text
GET /api/v1/doctors?search=smith
```

```text
GET /api/v1/staff?search=anna
```

This keeps search behavior predictable and authorization boundaries clear.

A global search feature may be introduced later if it provides real value.

---

# 26. Pagination Convention

List endpoints should use a consistent pagination model.

Example:

```text
?page=0&size=20
```

Response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

This is the standard response wrapper (`PageResponse`, D-006).

## Rules

* Default page size is 20.
* Maximum page size is 100; larger values are clamped.
* Total counts must reflect only authorized records.
* Pagination must work correctly with search/filter/sort.

---

# 27. Sorting Convention

Use a consistent format.

Example:

```text
?sort=lastName,asc
```

Multiple sort fields may be supported:

```text
?sort=lastName,asc&sort=createdAt,desc
```

Only approved sortable fields should be accepted.

Unsupported sort fields are rejected with `400 INVALID_SORT`.

---

# 28. Filtering Convention

Filters should use resource-specific query parameters.

Example:

```text
GET /api/v1/appointments
    ?doctorId=...
    &status=CONFIRMED
    &from=2027-04-01
    &to=2027-04-30
```

Filters must not allow users to bypass authorization.

---

# 29. Date and Time

The backend should use a consistent date/time representation.

Rules:

* Store timestamps consistently.
* Use explicit timezone handling.
* Clinic-local time should be used when interpreting appointment schedules.
* The frontend should format dates/times for the user.
* Appointment conflicts must be evaluated using authoritative backend time calculations.

Serialization follows `decisions.md` D-004: instants as ISO-8601 with offset (`2027-04-12T08:30:00Z`), dates as `YYYY-MM-DD`, local times as `HH:mm`.

---

# 30. Request Validation

Every API endpoint accepting user input must validate it.

Validation includes:

* Required fields
* String lengths
* Email format
* Date/time validity
* Numeric ranges
* Enum values
* Relationships between fields
* Business rules

Example:

A request to create an appointment must not be accepted merely because the JSON is structurally valid.

The backend must also verify:

* patient exists;
* doctor exists;
* both belong to the correct clinic;
* doctor is available;
* no conflict exists;
* requesting user is authorized.

---

# 31. Error Response Format

API errors use RFC 9457 Problem Details (`application/problem+json`) with IATROS extensions (D-014).

Example:

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

The frontend uses `code` and `fieldErrors[].code` to display translated feedback. `title` is for developers only and is never shown to users.

Internal stack traces must never be returned to users.

Shared error codes (resource-specific codes such as `PATIENT_NOT_FOUND` are documented with each endpoint):

| Status | Code                                   | Meaning                                                   |
| ------ | -------------------------------------- | --------------------------------------------------------- |
| 400    | `VALIDATION_ERROR`                     | One or more fields are invalid; see `fieldErrors`         |
| 400    | `MALFORMED_REQUEST`                    | Unreadable body or wrongly typed parameter                |
| 400    | `INVALID_SORT`                         | Sort field or direction not allowed                       |
| 400    | `INVALID_PAGE` / `INVALID_PAGE_SIZE`   | Negative page or non-positive size                        |
| 400    | `INVALID_TOKEN`                        | Invitation/reset token unknown, expired, or already used  |
| 401    | `UNAUTHENTICATED`                      | No valid session                                          |
| 401    | `INVALID_CREDENTIALS`                  | Login rejected (reason intentionally not disclosed)       |
| 403    | `FORBIDDEN`                            | The role may not perform this operation                   |
| 403    | `CSRF_TOKEN_INVALID`                   | Missing or invalid CSRF token; refresh it and retry       |
| 404    | `NOT_FOUND`                            | Unknown route                                             |
| 404    | `STAFF_NOT_FOUND`                      | No such member in the caller's clinic                     |
| 404    | `DOCTOR_NOT_FOUND`                     | No such doctor in the caller's clinic or data scope       |
| 404    | `SCHEDULE_EXCEPTION_NOT_FOUND`         | No such exception for this doctor                         |
| 404    | `PATIENT_NOT_FOUND`                    | No such patient in the caller's clinic or patient scope   |
| 404    | `ALLERGY_NOT_FOUND`, `MEDICAL_CONDITION_NOT_FOUND`, `MEDICATION_NOT_FOUND`, `MEDICAL_HISTORY_ENTRY_NOT_FOUND`, `MEDICAL_NOTE_NOT_FOUND` | No such entry in this patient's medical record |
| 404    | `CONSULTATION_NOT_FOUND`               | No such consultation in the caller's clinic or patient scope |
| 404    | `APPOINTMENT_NOT_FOUND`                | No such appointment in the caller's clinic or data scope  |
| 405    | `METHOD_NOT_ALLOWED`                   | HTTP method not supported on this route                   |
| 409    | `CONCURRENT_MODIFICATION`              | Someone else changed the resource; reload and retry       |
| 409    | `APPOINTMENT_CONFLICT`                 | The doctor already has an appointment at that time        |
| 409    | `EMAIL_ALREADY_USED`                   | Another account uses this email                           |
| 409    | `SCHEDULE_PERIOD_OVERLAP`              | Working periods of the same day overlap (database safety net; requests are normally rejected first with the `OVERLAP` field error) |
| 409    | `PRIMARY_DOCTOR_ALREADY_SET`           | The patient already has a primary doctor                  |
| 409    | `DOCTOR_ALREADY_AUTHORIZED`            | The doctor already has access to the patient              |
| 409    | `CONSULTATION_ALREADY_EXISTS`          | The appointment already has a consultation                |
| 409    | `DATA_CONFLICT`                        | Other integrity conflict                                  |
| 415    | `UNSUPPORTED_MEDIA_TYPE`               | Wrong `Content-Type`                                      |
| 422    | `INVALID_STATUS_TRANSITION`            | Status change not allowed by D-024                        |
| 422    | `APPOINTMENT_NOT_EDITABLE`             | Only `SCHEDULED`/`CONFIRMED` appointments can be edited   |
| 422    | `INVALID_TIME_RANGE` / `APPOINTMENT_TOO_LONG` | End before start, or longer than 12 hours          |
| 422    | `DOCTOR_UNAVAILABLE`                   | The time is outside the doctor's working hours (D-025)    |
| 422    | `APPOINTMENT_NOT_STARTED`              | Day-of statuses cannot be set before the appointment's date |
| 422    | `EMAIL_CHANGE_NOT_ALLOWED`             | Sign-in email is fixed once the account is activated      |
| 422    | `OWNER_STATUS_LOCKED`                  | The clinic owner cannot be deactivated                    |
| 422    | `STAFF_HAS_UPCOMING_APPOINTMENTS`      | Doctor still has upcoming appointments                    |
| 422    | `USER_NOT_INVITED`                     | Invitation resent for a member who is not `INVITED`       |
| 422    | `PATIENT_ARCHIVED`                     | Archived patients are read-only until restored            |
| 422    | `PATIENT_HAS_UPCOMING_APPOINTMENTS`    | Patient still has upcoming appointments                   |
| 422    | `PRIMARY_DOCTOR_REQUIRED`              | The primary doctor cannot be removed from a patient       |
| 429    | `RATE_LIMITED`                         | Too many attempts; retry after the `Retry-After` seconds  |
| 500    | `INTERNAL_ERROR`                       | Unexpected error; nothing internal is exposed             |

Field error codes: `REQUIRED`, `INVALID_LENGTH`, `INVALID_EMAIL`, `INVALID_FORMAT`, `OUT_OF_RANGE`, `INVALID_DATE`, `INVALID`, `PASSWORD_MISMATCH`, `INCORRECT_PASSWORD`, `END_BEFORE_START`, `OVERLAP`, `DATE_IN_PAST`, `DATE_IN_FUTURE`, `DOCTOR_DEACTIVATED`, `BEFORE_CONSULTATION`.

---

# 32. HTTP Status Codes

Use standard HTTP semantics.

Examples:

| Status | Meaning                                            |
| ------ | -------------------------------------------------- |
| 200    | Successful request                                 |
| 201    | Resource created                                   |
| 204    | Successful request with no response body           |
| 400    | Invalid request                                    |
| 401    | Not authenticated                                  |
| 403    | Authenticated, but the role may not use this operation |
| 404    | Not found, other clinic, or outside the user's data scope |
| 409    | State conflict (e.g. `APPOINTMENT_CONFLICT`)       |
| 422    | Business-rule violation (e.g. `DOCTOR_UNAVAILABLE`) |
| 500    | Unexpected server error                            |

`400` is used for malformed requests and field validation. `422` is used for well-formed requests that break a business rule.

Resources outside the user's clinic or authorized scope return `404`, not `403`, so their existence is not revealed.

---

# 33. Authorization Rules

Every protected endpoint must enforce authorization.

Authorization must consider:

```text
Authenticated User
        +
Role
        +
Clinic
        +
Resource
        +
Resource Relationship
```

Example:

A doctor requesting:

```text
GET /api/v1/patients/{patientId}/medical-record
```

must not receive the record simply because the patient belongs to the same clinic.

The backend must verify that the doctor is authorized to access that patient.

---

# 34. Clinic Isolation

Every clinic-scoped resource must be isolated.

A user from Clinic A must never be able to access Clinic B's:

* Patients
* Doctors
* Staff
* Appointments
* Medical records
* Consultations
* Documents
* KPIs
* Schedules

This must be enforced server-side.

The frontend must never be responsible for clinic isolation.

---

# 35. DTO Policy

The API should use DTOs rather than exposing JPA entities directly.

Typical DTO categories:

* Request DTO
* Response DTO
* Summary DTO
* Detailed DTO
* Page DTO

Example:

```text
CreatePatientRequest
UpdatePatientRequest
PatientSummaryResponse
PatientDetailResponse
```

This prevents database structure from automatically becoming the public API contract.

---

# 36. API and Database Separation

The API must not assume that the database schema is the public API.

Database changes should be possible without unnecessarily breaking API consumers.

Similarly, API DTOs should not expose internal database relationships that users do not need.

---

# 37. Transaction Boundaries

Operations that modify multiple related records must use appropriate transactions.

Examples:

### Creating a doctor

Potentially involves:

* User
* Staff information
* Doctor profile
* Role
* Clinic association

### Booking an appointment

May involve:

* Patient validation
* Doctor validation
* Schedule validation
* Conflict detection
* Appointment creation

These operations must maintain data integrity if one part fails.

---

# 38. Concurrency

Appointment creation must account for concurrent requests.

Example:

Two users attempt to book the same doctor's 10:00 appointment at almost the same time.

The backend must prevent both requests from successfully creating conflicting appointments.

Frontend checks alone are insufficient.

---

# 39. Idempotency

Where repeated requests could cause harmful duplication, the API should use appropriate safeguards.

Examples:

* Duplicate appointment creation
* Repeated form submission
* Repeated status changes
* Document uploads

The implementation strategy may include request identifiers or appropriate business constraints where necessary.

---

# 40. API Security Principles

The API must:

* Require authentication for protected resources.
* Enforce authorization server-side.
* Validate all input.
* Never trust client-provided clinic IDs for authorization.
* Never trust client-provided user roles.
* Never expose passwords.
* Avoid leaking whether sensitive accounts exist.
* Protect medical records.
* Protect document access.
* Log security-relevant events where appropriate.
* Use HTTPS in deployed environments.

Detailed security requirements are defined separately in `security.md`.

---

# 41. API Documentation

The API should eventually be documented using an OpenAPI-compatible specification.

The documentation should describe:

* Endpoints
* Parameters
* Request bodies
* Response bodies
* Authentication requirements
* Error responses
* Pagination
* Validation

Swagger/OpenAPI tooling may be introduced during implementation.

---

# 42. API Testing

Every important API module should have tests for:

### Functional behavior

* Valid requests
* Invalid requests
* Successful responses
* Error responses

### Authorization

* Owner access
* Doctor access
* Secretary access
* Unauthorized resource access
* Cross-clinic access attempts

### Business rules

* Appointment conflicts
* Doctor availability
* Patient-doctor authorization
* Invalid status transitions
* Invalid relationships

### Data integrity

* Transaction rollback
* Duplicate prevention
* Concurrent operations where relevant

Authorization tests are particularly important because hiding an item in the frontend is not sufficient protection.

---

# 43. API Module Boundaries

The API should follow the business modules.

High-level grouping:

```text
/api/v1/auth
/api/v1/clinic
/api/v1/staff
/api/v1/doctors
/api/v1/specialties
/api/v1/patients
/api/v1/consultations
/api/v1/appointments
/api/v1/calendar
/api/v1/documents
/api/v1/dashboard
/api/v1/kpis
/api/v1/activity
```

These are API boundaries, not necessarily one-to-one Java packages.

---

# 44. Future API Extensions

The API should leave room for future capabilities without implementing them in V1.

Potential future endpoints:

```text
/api/v1/patient-portal
/api/v1/notifications
/api/v1/integrations/google-calendar
/api/v1/integrations/outlook
/api/v1/payments
/api/v1/subscriptions
/api/v1/specialties/{specialty}
```

These should only be introduced when the corresponding product feature is approved.

---

# 45. API Definition of Done

An API endpoint is not considered complete merely because it returns the expected data.

It must have:

* Correct authentication
* Correct authorization
* Clinic isolation
* Input validation
* Business-rule validation
* Consistent response format
* Consistent error handling
* Pagination where applicable
* Search/filter/sort where applicable
* Appropriate transaction boundaries
* Tests
* API documentation

---

# 46. Final API Principle

The IATROS API must be treated as a **business API**, not simply a CRUD wrapper around PostgreSQL.

The backend is the authoritative source for:

**identity → authorization → validation → business rules → data integrity**

The frontend should provide a fast and intuitive user experience while the API guarantees that the underlying clinic data remains correct and secure.
