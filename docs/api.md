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

Response should contain the authenticated user's session/authentication information required by the frontend.

The exact token/session mechanism will be finalized in `security.md`.

---

## Logout

```text
POST /api/v1/auth/logout
```

The endpoint invalidates the appropriate authentication state where required.

---

## Current User

```text
GET /api/v1/auth/me
```

Returns:

* User ID
* Name
* Email
* Role
* Clinic
* Relevant profile information

The frontend should use this endpoint to establish the current authenticated user's context.

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

The response should avoid revealing whether a particular email exists in the system.

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

---

# 6. Clinic API

## Get Current Clinic

```text
GET /api/v1/clinic
```

## Update Clinic

```text
PUT /api/v1/clinic
```

Owner-only operation.

Possible data:

```json
{
  "name": "Example Medical Clinic",
  "phone": "...",
  "email": "...",
  "address": "..."
}
```

---

# 7. Staff API

## List Staff

```text
GET /api/v1/staff
```

Supports:

* Search
* Filtering
* Sorting
* Pagination

Example:

```text
GET /api/v1/staff?page=0&size=20&search=smith&sort=lastName,asc
```

## Get Staff Member

```text
GET /api/v1/staff/{id}
```

## Create Staff Member

```text
POST /api/v1/staff
```

## Update Staff Member

```text
PUT /api/v1/staff/{id}
```

## Deactivate Staff Member

```text
PATCH /api/v1/staff/{id}/status
```

## Reactivate Staff Member

```text
PATCH /api/v1/staff/{id}/status
```

The backend must prevent invalid role or clinic changes.

---

# 8. Doctor API

Doctors are staff members with a doctor-specific profile.

## List Doctors

```text
GET /api/v1/doctors
```

Supports:

* Search
* Filter by specialty
* Filter by status
* Sorting
* Pagination

## Get Doctor

```text
GET /api/v1/doctors/{id}
```

## Create Doctor

```text
POST /api/v1/doctors
```

This may internally create or associate the corresponding user/staff record.

## Update Doctor

```text
PUT /api/v1/doctors/{id}
```

## Get Doctor Profile

```text
GET /api/v1/doctors/{id}/profile
```

## Get Doctor Patients

```text
GET /api/v1/doctors/{id}/patients
```

The backend must ensure that the requesting user is authorized to access the requested doctor's patient data.

---

# 9. Specialty API

## List Specialties

```text
GET /api/v1/specialties
```

Specialties should be represented as structured data for primary specialty selection.

Doctors may additionally have free-text additional specialty information.

The initial specialty list may be managed as controlled application data rather than exposing full specialty administration to users.

---

# 10. Patient API

## List Patients

```text
GET /api/v1/patients
```

Supports:

* Search
* Filtering
* Sorting
* Pagination

Example:

```text
GET /api/v1/patients?page=0&size=20&search=john&sort=lastName,asc
```

The returned dataset must already respect the requesting user's authorization.

---

## Get Patient

```text
GET /api/v1/patients/{id}
```

## Create Patient

```text
POST /api/v1/patients
```

## Update Patient

```text
PUT /api/v1/patients/{id}
```

## Assign Primary Doctor

```text
PATCH /api/v1/patients/{id}/primary-doctor
```

## Manage Authorized Doctors

```text
GET /api/v1/patients/{id}/doctors
```

```text
POST /api/v1/patients/{id}/doctors
```

```text
DELETE /api/v1/patients/{id}/doctors/{doctorId}
```

These operations must enforce appropriate owner/doctor permissions.

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

Every patient has one medical record.

## Get Medical Record

```text
GET /api/v1/patients/{patientId}/medical-record
```

## Update Medical Record

```text
PUT /api/v1/patients/{patientId}/medical-record
```

Core information includes:

* Allergies
* Chronic conditions
* Current medications
* Past medical history
* General medical notes

Clinical history is handled through consultations.

---

# 13. Medical Conditions API

Where structured condition records are used:

```text
GET /api/v1/patients/{patientId}/medical-record/conditions
```

```text
POST /api/v1/patients/{patientId}/medical-record/conditions
```

```text
PUT /api/v1/patients/{patientId}/medical-record/conditions/{conditionId}
```

```text
DELETE /api/v1/patients/{patientId}/medical-record/conditions/{conditionId}
```

Only authorized users may access or modify these records.

---

# 14. Allergies API

```text
GET /api/v1/patients/{patientId}/medical-record/allergies
```

```text
POST /api/v1/patients/{patientId}/medical-record/allergies
```

```text
PUT /api/v1/patients/{patientId}/medical-record/allergies/{allergyId}
```

```text
DELETE /api/v1/patients/{patientId}/medical-record/allergies/{allergyId}
```

---

# 15. Medications API

```text
GET /api/v1/patients/{patientId}/medical-record/medications
```

```text
POST /api/v1/patients/{patientId}/medical-record/medications
```

```text
PUT /api/v1/patients/{patientId}/medical-record/medications/{medicationId}
```

```text
DELETE /api/v1/patients/{patientId}/medical-record/medications/{medicationId}
```

The V1 system does not implement a full electronic prescribing system.

---

# 16. Consultation API

## List Consultations

```text
GET /api/v1/patients/{patientId}/consultations
```

Supports:

* Pagination
* Sorting
* Date filtering
* Doctor filtering where authorized

## Get Consultation

```text
GET /api/v1/consultations/{id}
```

## Create Consultation

```text
POST /api/v1/patients/{patientId}/consultations
```

## Update Consultation

```text
PUT /api/v1/consultations/{id}
```

A consultation may reference an appointment.

Example:

```json
{
  "doctorId": "...",
  "appointmentId": "...",
  "dateTime": "...",
  "reason": "Follow-up",
  "diagnosis": "...",
  "clinicalNotes": "...",
  "treatmentNotes": "...",
  "followUpNotes": "..."
}
```

---

# 17. Appointment API

## List Appointments

```text
GET /api/v1/appointments
```

The endpoint supports:

* Date range
* Doctor
* Patient
* Status
* Appointment type
* Pagination where a list response is needed
* Sorting where applicable

Example:

```text
GET /api/v1/appointments?from=2027-04-01&to=2027-04-30&doctorId=...
```

---

## Get Appointment

```text
GET /api/v1/appointments/{id}
```

## Create Appointment

```text
POST /api/v1/appointments
```

## Update Appointment

```text
PUT /api/v1/appointments/{id}
```

## Reschedule Appointment

```text
PATCH /api/v1/appointments/{id}/schedule
```

## Change Appointment Status

```text
PATCH /api/v1/appointments/{id}/status
```

## Cancel Appointment

```text
POST /api/v1/appointments/{id}/cancel
```

Cancellation should preserve the appointment record and historical status.

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

---

# 19. Calendar API

The calendar can use the appointment API rather than having an entirely separate calendar data model.

Example:

```text
GET /api/v1/calendar/events
```

Parameters may include:

* Start date
* End date
* Doctor
* Status

The response should contain the data required to render calendar events efficiently.

---

# 20. Doctor Schedule API

## Get Schedule

```text
GET /api/v1/doctors/{doctorId}/schedule
```

## Update Schedule

```text
PUT /api/v1/doctors/{doctorId}/schedule
```

## Add Working Period

```text
POST /api/v1/doctors/{doctorId}/schedule/periods
```

## Update Working Period

```text
PUT /api/v1/doctors/{doctorId}/schedule/periods/{periodId}
```

## Remove Working Period

```text
DELETE /api/v1/doctors/{doctorId}/schedule/periods/{periodId}
```

## Schedule Exceptions

Where implemented:

```text
GET /api/v1/doctors/{doctorId}/schedule/exceptions
```

```text
POST /api/v1/doctors/{doctorId}/schedule/exceptions
```

```text
DELETE /api/v1/doctors/{doctorId}/schedule/exceptions/{id}
```

---

# 21. Document API

## List Patient Documents

```text
GET /api/v1/patients/{patientId}/documents
```

Supports pagination.

## Get Document Metadata

```text
GET /api/v1/documents/{id}
```

## Upload Document

```text
POST /api/v1/patients/{patientId}/documents
```

Uses multipart/form-data where appropriate.

## Download/View Document

```text
GET /api/v1/documents/{id}/content
```

Authorization must be checked before returning the file.

## Delete/Archive Document

```text
DELETE /api/v1/documents/{id}
```

The final deletion/archival policy will be defined by the security/data rules.

---

# 22. Dashboard API

Dashboard endpoints should provide aggregated information rather than requiring the frontend to make a large number of unrelated requests.

## Owner Dashboard

```text
GET /api/v1/dashboard/owner
```

## Doctor Dashboard

```text
GET /api/v1/dashboard/doctor
```

The backend determines what information the requesting user is allowed to receive.

The frontend must not request another user's dashboard.

---

# 23. KPI API

## Clinic KPIs

```text
GET /api/v1/kpis/clinic
```

Owner-only.

## Doctor KPIs

```text
GET /api/v1/kpis/doctors/{doctorId}
```

A doctor may access their own KPI data.

The owner may access doctor KPI data according to owner permissions.

---

# 24. Activity API

## Clinic Activity

```text
GET /api/v1/activity
```

Supports appropriate:

* Date filtering
* Activity type filtering
* Pagination

The endpoint must return only activity the requesting user is authorized to view.

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

The exact response wrapper may be standardized during implementation.

## Rules

* Default page size should be reasonable.
* Maximum page size should be enforced by the backend.
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

The backend should reject or safely ignore unsupported sort fields according to the API policy.

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

The exact serialization standard will be finalized during implementation.

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

API errors should follow a consistent structure.

Example:

```json
{
  "timestamp": "2027-04-12T10:30:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "The request contains invalid data.",
  "fieldErrors": {
    "startTime": "The selected time is unavailable."
  },
  "path": "/api/v1/appointments"
}
```

The frontend should use the error code and field information to display appropriate feedback.

Internal stack traces must never be returned to users.

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
| 403    | Authenticated but not authorized                   |
| 404    | Resource not found                                 |
| 409    | Business/data conflict                             |
| 422    | Validation/business-rule failure where appropriate |
| 500    | Unexpected server error                            |

The exact use of `400` versus `422` should be standardized during implementation.

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
/api/v1/schedules
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
