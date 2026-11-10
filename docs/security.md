# IATROS — Security Specification

## 1. Purpose

This document defines the security requirements and principles for IATROS.

IATROS handles sensitive clinic information, including:

* Patient personal information
* Medical records
* Diagnoses
* Medications
* Consultations
* Medical documents
* Staff accounts
* Appointment information

Security must therefore be treated as a core product requirement rather than an additional feature.

The primary security objectives are:

1. Confidentiality
2. Integrity
3. Availability
4. Strong clinic isolation
5. Correct role-based access
6. Protection of medical information
7. Auditability of sensitive actions

---

# 2. Security Principles

IATROS follows these principles:

### 2.1 Never trust the frontend

The frontend is a user interface, not a security boundary.

Hiding a button does not prevent a user from calling the API directly.

Every sensitive operation must be protected by the backend.

### 2.2 Least privilege

Users should receive only the access required for their role and responsibilities.

### 2.3 Deny by default

If an operation is not explicitly permitted, it should be rejected.

### 2.4 Clinic isolation

Users must never access another clinic's data.

### 2.5 Authorization at resource level

Role checks alone are not sufficient.

For example:

```text
Doctor + Clinic membership
```

does not automatically mean:

```text
Doctor + access to every patient in that clinic
```

The doctor must also be authorized to access the particular patient.

---

# 3. Security Boundaries

The main security boundaries are:

```text
Internet
   │
   ▼
Frontend
   │
   ▼
Authentication
   │
   ▼
Authorization
   │
   ▼
Business Services
   │
   ▼
Database
```

The frontend must never bypass the backend to reach protected data.

---

# 4. Authentication

Authentication determines the identity of the user.

V1 supports:

* Clinic Owner
* Doctor
* Secretary

Authentication should support:

* Login
* Logout
* Password reset
* Password change
* Current-user retrieval

---

# 5. Password Security

Passwords must never be stored as plaintext.

Passwords must be stored using a modern, strong, one-way password hashing algorithm supported by the chosen Spring Security configuration.

**Decision:** `DelegatingPasswordEncoder` with Argon2id as the default (`decisions.md` D-013). Password policy is length only: 8–128 characters, no composition rules.

Requirements:

* Password hashes only
* Never log passwords
* Never return password hashes through APIs
* Never include passwords in error messages
* Never commit passwords to Git

---

# 6. Authentication Tokens / Sessions

**Decision (`decisions.md` D-010):** server-side sessions managed by Spring Security and stored in PostgreSQL via Spring Session JDBC.

* Session cookie: `HttpOnly`, `Secure` outside local development, `SameSite=Lax`.
* No authentication token is readable by JavaScript or stored in `localStorage`.
* Idle timeout 30 minutes; absolute lifetime 12 hours.
* Session ID is rotated on login (session-fixation protection).
* Logout, deactivation, and password change invalidate the relevant sessions on the server immediately.

Requirements:

* Authentication credentials must be protected.
* Tokens/session identifiers must not be unnecessarily exposed.
* Expiration must be supported.
* Logout/revocation behavior must be defined.
* Sensitive authentication information must not be logged.
* Authentication failures should not expose sensitive account information.

The implementation should favor a secure, standard Spring Security approach rather than custom authentication logic.

---

# 7. Forgot Password

The password-reset process must be designed to prevent account enumeration.

For example, the API should not reveal:

> "This email does not belong to an account."

Instead, the user should receive a generic response indicating that reset instructions will be provided if the account is eligible.

Reset tokens must:

* Be unpredictable.
* Expire.
* Be single-use.
* Be securely stored or represented.
* Not appear in application logs.

After successful password reset, the token must no longer be usable.

---

# 8. Authorization Model

IATROS uses role-based access control combined with resource-level authorization.

## Roles

### Owner

Full clinic-management access.

### Doctor

Access to their own professional/operational information and authorized patient/clinical information.

### Secretary

Access focused on patient booking and calendar operations.

### Patient

Architecturally supported for the future, but not implemented in V1.

---

# 9. Owner Security Scope

The owner can access:

* Clinic settings
* Staff
* Doctors
* Patients
* Medical records
* Consultations
* Appointments
* Schedules
* Clinic activity
* KPIs

Owner access is still subject to authentication and general security controls.

Sensitive operations should be auditable.

---

# 10. Doctor Security Scope

Doctors can access:

* Their own profile
* Their own schedule
* Their own appointments
* Their own KPIs
* Authorized patients
* Medical records belonging to authorized patients
* Consultations involving authorized patients

A doctor must not automatically access:

* Other doctors' private patient relationships
* Other doctors' private operational data
* Clinic administration
* Staff management
* Clinic settings
* Unrestricted clinic-wide KPIs

---

# 11. Secretary Security Scope

Secretaries can access the information required to perform booking and calendar operations.

They may access:

* Calendar
* Appointment information
* Patient information required for booking

They must not have unrestricted access to:

* Medical records
* Consultations
* Diagnoses
* Clinical notes
* Treatment information
* Staff administration
* Clinic administration
* Clinic KPIs

The exact patient fields exposed to secretaries are defined in `decisions.md` D-029.

---

# 12. Patient-Doctor Authorization

Patients belong to the clinic.

They are not permanently owned by one doctor.

A patient can have:

* One primary doctor
* Additional authorized doctors

Doctor access must be based on this relationship.

Example:

```text
Patient A
 ├── Primary Doctor: Dr. Smith
 └── Authorized Doctor: Dr. Brown
```

Dr. Smith and Dr. Brown may access the patient's authorized clinical information.

Dr. Jones, who has no authorized relationship with Patient A, must not automatically receive access simply because Dr. Jones works at the same clinic.

---

# 13. Authorization Checks

Sensitive requests should follow this conceptual process:

```text
1. Is the user authenticated?
        ↓
2. Which clinic does the user belong to?
        ↓
3. What role does the user have?
        ↓
4. What resource is being requested?
        ↓
5. Does the resource belong to the user's clinic?
        ↓
6. Is the user authorized for this specific resource?
        ↓
7. Is the requested operation allowed?
        ↓
8. Execute business operation
```

The service layer should enforce resource-specific rules.

---

# 14. Clinic Isolation

Clinic isolation is one of the most important security requirements.

Every clinic-owned resource must be associated with a clinic directly or indirectly.

Examples:

* Users
* Doctors
* Patients
* Appointments
* Consultations
* Medical records
* Documents
* Schedules
* KPIs

A request must never allow a user to select an arbitrary clinic ID and gain access to another clinic.

The backend should derive the user's clinic from the authenticated security context.

Client-provided clinic identifiers must not be trusted for authorization.

---

# 15. Cross-Clinic Attack Prevention

The system must explicitly prevent scenarios such as:

```text
User from Clinic A
        ↓
Requests Patient belonging to Clinic B
        ↓
404 Not Found (existence is not revealed)
```

Resources outside the user's clinic or authorized scope return `404`. `403` is reserved for operations the user's role may never perform (`decisions.md` D-014).

This must apply to:

* Direct IDs
* Search
* Filters
* Sorting
* Pagination
* Aggregations
* Dashboard data
* Documents
* Nested resources

A user must not be able to discover another clinic's information indirectly.

**Implementation (Phase 13):** `SecurityAbuseTests` is an abuse matrix that discovers every `/api/v1` route with a path variable from the running application (50 routes at the time of writing) and calls it with real IDs of clinic A, using valid request bodies so that refusals come from authorization rather than validation:

* Another clinic's owner (the most privileged outsider) must receive `404` on every route, with no clinic A data in any response, no audit event in clinic A, and an unchanged view of every attacked resource afterwards.
* A secretary must receive `403` on every clinical route (medical record, consultations, documents).
* A doctor who is not linked to the patient must receive `403` or `404` on every route except reading colleagues' schedules, and must change nothing.
* Clinic A's IDs placed in request bodies (booking, rescheduling, primary or authorized doctor, consultation-to-appointment links, document-to-consultation links) are rejected with `4xx`, and clinic A's IDs used as filters (patients, appointments, availability, calendar, activity, KPIs, consultations, documents) return no clinic A data.
* A positive control: clinic A's own owner receives `200` on every `GET` route of the matrix, which proves the IDs and URLs are real.

A new route that accepts a request body fails the matrix until it is given an attack body, so new endpoints cannot silently escape it.

---

# 16. Medical Record Security

Medical records are highly sensitive.

Access must be restricted according to the established role and patient authorization model.

Medical records include:

* Allergies
* Conditions
* Medications
* Medical history
* Diagnoses
* Clinical notes
* Treatment notes
* Follow-up information
* Consultation history
* Medical documents

Every read and modification must pass authorization checks.

---

# 17. Document Security

Documents must be protected independently from the frontend.

A document URL or ID must not be sufficient to access a file.

For example:

```text
GET /api/v1/documents/{id}/content
```

must verify:

* authenticated user;
* clinic;
* resource ownership;
* patient authorization;
* document access permission.

Direct public file URLs should not be used for sensitive medical documents.

Implementation (D-044): every document route checks clinical access to the patient before opening the file; another clinic's, an unlinked patient's, or an archived document is `404 DOCUMENT_NOT_FOUND`. Files are served with their verified content type, `X-Content-Type-Options: nosniff`, and no-cache headers, and each view or download is audited as `DOCUMENT_VIEWED` without the file name.

---

# 18. Data Minimization

Users should receive only the data required for their workflow.

For example, the secretary may need:

* Patient name
* Contact information
* Appointment information

but does not need:

* Diagnosis
* Medication history
* Clinical notes

API response DTOs should therefore be designed according to role and use case rather than simply returning complete database records.

---

# 19. API Security

Protected endpoints must require authentication.

Authorization must be enforced independently for each protected operation.

The backend must validate:

* Request body
* Query parameters
* Path variables
* Resource relationships
* Business constraints

The API must never trust:

* Client-provided role
* Client-provided clinic
* Client-provided ownership
* Client-provided authorization
* Hidden frontend fields

---

# 20. Input Validation

All external input must be validated.

Examples:

* Email
* Password
* Names
* Dates
* Appointment times
* IDs
* Enum values
* File metadata
* Text lengths

Validation protects both data integrity and application security.

---

# 21. SQL / Database Security

The application should use Spring Data JPA/Hibernate appropriately rather than constructing unsafe SQL strings from user input.

If native SQL is required:

* Parameters must be bound safely.
* User input must never be concatenated into SQL statements.
* Database permissions should follow least privilege.

The application database account should not have unnecessary administrative privileges.

---

# 22. Sensitive Data in Logs

Logs must not contain:

* Passwords
* Authentication tokens
* Password-reset tokens
* Full medical records
* Sensitive medical notes
* Private document contents

Identifiers may be logged where necessary for troubleshooting, provided they do not unnecessarily expose sensitive information.

---

# 23. Audit Logging

Sensitive actions should be auditable.

Potential audit events include:

* Login attempts
* Password changes
* Staff creation
* Staff deactivation
* Role changes
* Patient creation
* Patient updates
* Medical-record modifications
* Consultation creation/modification
* Document operations
* Appointment changes
* Permission-sensitive actions

An audit record should generally capture information such as:

* User
* Clinic
* Action
* Resource type
* Resource identifier
* Timestamp
* Relevant contextual information

Audit logs should themselves be protected from unauthorized modification.

---

# 24. Medical Data Modification

Changes to clinical information must preserve data integrity.

The system should avoid silently overwriting important historical information where an audit/history mechanism is required.

For example, modifying a consultation should not accidentally remove its historical context without an appropriate record of the change.

The exact medical-record versioning strategy can be refined during implementation.

---

# 25. Appointment Security

Appointment operations must verify:

* User authorization
* Clinic
* Patient
* Doctor
* Schedule
* Existing conflicts
* Valid status transitions

A user must not be able to modify another doctor's appointments simply by changing a `doctorId` in the request.

The backend must determine whether the authenticated user is authorized to perform the operation.

---

# 26. File Upload Security

Uploaded files must be treated as untrusted input.

The system should validate:

* File size
* File type
* File extension
* Content type
* Storage path/name

Uploaded filenames should not directly determine filesystem paths.

Files should be stored using generated identifiers rather than trusting user-provided filenames.

Storage: `DocumentStorage` abstraction; local filesystem in development, S3-compatible storage in production. Defaults: 20 MB maximum, PDF/JPEG/PNG/WEBP only, type verified from file signature (`decisions.md` D-032). The extension must also match the detected content, so a renamed file is rejected; display names are stripped of path parts and control characters; storage keys are generated UUIDs, and the local adapter rejects any other key (D-044).

---

# 27. Frontend Security

The React application should:

* Avoid storing sensitive information unnecessarily.
* Avoid exposing secrets in frontend code.
* Never contain database credentials.
* Never contain server-side API secrets.
* Avoid putting sensitive information into URLs unnecessarily.
* Handle authentication state securely.
* Clear authentication state on logout.

Frontend route guards improve UX but are not a replacement for backend authorization.

### Browser hardening headers (`decisions.md` D-045)

Every API response, including errors and public auth endpoints, carries:

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` |
| `Referrer-Policy` | `no-referrer` |
| `Cross-Origin-Resource-Policy` | `same-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` |
| `Strict-Transport-Security` | sent on HTTPS requests (behind the proxy, via `forward-headers-strategy: native`) |

The API returns only JSON and stored documents, so its policy allows nothing to load, run, submit, or frame. A document opened inline (for example a PDF) therefore cannot execute script even if a crafted file passed validation. `sandbox` is deliberately not used because it disables browsers' built-in PDF viewers.

The single-page application's HTML, scripts, and styles are served by the reverse proxy, which must send this policy for them:

```text
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;
  font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self';
  frame-ancestors 'none'
Referrer-Policy: no-referrer
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

The built application contains no inline script or style: the pre-paint theme and language script is the static file `public/theme-init.js`, so the policy needs no `'unsafe-inline'` or hashes. Keep it that way; a new inline script would be blocked in production.

---

# 28. Secrets Management

Secrets must never be committed to Git.

Examples:

* Database passwords
* Authentication secrets
* Encryption keys
* Email credentials
* Storage credentials
* API keys

Development configuration should use environment variables or an appropriate local secret mechanism.

Production secrets must be managed using the deployment environment's secure configuration facilities.

---

# 29. Configuration Separation

Development, testing, and production configurations should be separated.

Examples:

```text
application-dev
application-test
application-prod
```

Sensitive production configuration must not be copied into source control.

---

# 30. HTTPS

Production communication must use HTTPS.

Sensitive information, including authentication credentials and medical data, must never be transmitted over unencrypted HTTP in production.

HTTP-to-HTTPS redirection may be configured at the infrastructure level.

---

# 31. CORS

Cross-Origin Resource Sharing must be explicitly configured.

The backend should not allow unrestricted origins in production.

Development origins may be explicitly configured for the local React application.

Wildcard CORS configuration should not be used as a shortcut.

**Decision (`decisions.md` D-012):** the frontend and API share one origin in production (reverse proxy), and the Vite dev server proxies `/api` in development. CORS is therefore disabled by default.

---

# 32. CSRF

The CSRF strategy must be determined based on the final authentication/session architecture.

The implementation must not simply disable CSRF protection without evaluating whether the chosen authentication mechanism requires it.

**Decision (`decisions.md` D-011):** because authentication is cookie-based, CSRF protection remains enabled using `CookieCsrfTokenRepository`. The frontend echoes the `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header on every state-changing request.

---

# 33. Rate Limiting

Rate limiting should be considered for endpoints that are susceptible to abuse.

Priority areas include:

* Login
* Forgot password
* Password reset
* Potentially file uploads
* Other publicly reachable endpoints

The exact limits can be defined during deployment/security hardening.

**Client IP behind the proxy:** per-IP limits use the address Tomcat resolves from `X-Forwarded-For` (`forward-headers-strategy: native`). Tomcat only trusts that header from internal-network proxies (its default `internal-proxies` pattern covers private and loopback ranges), so a client cannot choose its own address. If the reverse proxy reaches the backend from a public address, set `server.tomcat.remoteip.internal-proxies` to exactly that address; otherwise every request appears to come from the proxy and all users would share one per-IP budget.

---

# 34. Account Protection

The authentication system should protect against repeated automated login attempts.

Possible mechanisms include:

* Rate limiting
* Temporary lockout or throttling
* Monitoring suspicious attempts

The exact mechanism should balance security with usability.

---

# 35. Session Security

Authentication sessions/tokens should have:

* Appropriate expiration
* Secure transport
* Appropriate storage
* Logout handling
* Revocation strategy where applicable

Long-lived authentication credentials should be avoided unless there is a clear security reason for them.

---

# 36. Error Security

Errors must provide enough information for the user to understand what happened without exposing internal details.

Never expose:

* Stack traces
* SQL queries
* Database schema details
* Internal class names
* Authentication secrets
* Internal filesystem paths

The API should return standardized error responses.

---

# 37. Resource Enumeration Protection

Sensitive resources should not become discoverable simply by incrementing IDs.

For example:

```text
/api/v1/patients/100
/api/v1/patients/101
/api/v1/patients/102
```

must not allow a doctor to discover patients they are not authorized to access.

Authorization must happen before returning resource data.

Account enumeration is resisted the same way: login checks the password before the account status, performs a dummy hash for unknown emails, and audits a failure identically whether or not the account exists; forgot-password always returns `202` and sends mail on a background executor, so its timing does not depend on whether a reset email was sent.

**Decision:** public identifiers are UUID v7 (`decisions.md` D-003). Non-sequential IDs are defense in depth only; authorization checks remain mandatory.

---

# 38. Search Security

Search is part of the security boundary.

For example, a doctor searching:

```text
search=John
```

must receive only patients they are authorized to access.

The system must not:

1. search every patient;
2. return unauthorized results;
3. filter them out only in React.

Authorization must be incorporated into the backend query/service logic.

---

# 39. Pagination Security

Pagination must also respect authorization.

For example, a doctor must not receive:

```text
totalElements = 4,283
```

if only 27 patients are actually accessible to them.

Pagination metadata must represent the authorized dataset.

---

# 40. Dashboard Security

Aggregated data can leak information even when individual records are hidden.

For example, a doctor should not receive a clinic-wide statistic such as:

```text
Total clinic appointments: 1,843
```

unless that information is explicitly permitted.

All dashboard and KPI calculations must use authorized datasets.

---

# 41. Database Integrity

Security also depends on data integrity.

The database should enforce appropriate constraints such as:

* Foreign keys, including composite `(clinic_id, id)` keys that keep child rows and their authors inside one clinic
* Unique constraints
* Not-null constraints
* Valid relationships, including exactly one primary doctor per patient and a doctor profile only for `DOCTOR` or `OWNER`
* Appropriate indexes

Business rules should be enforced in the service layer, while fundamental structural integrity should also be protected by the database.

---

# 42. Deactivation vs Deletion

Staff accounts should generally be deactivated rather than physically deleted when historical records depend on them.

This preserves:

* Appointment history
* Consultation attribution
* Audit records
* Historical activity

The exact soft-delete/archive policy for each entity will be finalized during implementation.

---

# 43. Dependency Security

Project dependencies must be kept reasonably current.

The project should periodically check for:

* Vulnerable Spring dependencies
* Vulnerable npm packages
* Vulnerable third-party libraries

Dependencies should not be added without a reason.

**Process:**

* Frontend: `npm audit` must report no vulnerabilities before a release.
* Backend: almost every library version is managed by the Spring Boot parent, so staying on the latest patch release of a supported Spring Boot line is the main control. Check with `.\mvnw.cmd versions:display-parent-updates versions:display-property-updates`, and ignore milestone (`-M`) and release-candidate (`-RC`) versions. The only directly pinned library is BouncyCastle (`bouncycastle.version`, used for Argon2).
* A full CVE scan of the backend (for example OWASP Dependency-Check, which requires an NVD API key) belongs in the CI pipeline once the repository has one.

Phase 13 baseline (October 2026): `npm audit` reported 0 vulnerabilities; Spring Boot 4.0.8 (the latest 4.0 patch) and BouncyCastle 1.86 (the latest release).

---

# 44. GitHub Security

Because IATROS will be published on GitHub:

The repository must never contain:

* Production credentials
* `.env` secrets
* Database passwords
* Private API keys
* Authentication secrets
* Real patient data
* Real medical documents

Development data must use synthetic/demo information.

A `.gitignore` must protect local configuration and generated files.

---

# 45. Test Data

Testing and GitHub demonstrations must use fictional data.

Never use:

* Real patient names
* Real medical histories
* Real diagnoses
* Real documents
* Real contact information

The repository should include clearly fictional sample data if demo data is required.

---

# 46. Security Testing

Security tests should include:

### Authentication

* Invalid login
* Expired authentication
* Logout
* Password reset

### Authorization

* Owner access
* Doctor access
* Secretary access
* Unauthorized role access
* Unauthorized patient access
* Unauthorized appointment access

### Clinic isolation

* Clinic A → Clinic B access attempt
* Cross-clinic search
* Cross-clinic IDs
* Cross-clinic documents
* Cross-clinic dashboard data

### Medical records

* Unauthorized record access
* Unauthorized consultation access
* Unauthorized document access

### Input security

* Invalid IDs
* Invalid parameters
* Oversized input
* Invalid files
* Malformed requests

---

# 47. Security Definition of Done

A security-sensitive feature is not complete until it has:

* Authentication requirements defined
* Authorization requirements defined
* Clinic isolation verified
* Input validation
* Appropriate DTOs
* Error handling
* Sensitive-data protection
* Relevant audit logging
* Security tests
* No secrets committed
* No real patient data
* Documentation where necessary

---

# 48. Security Priority

Security priority for IATROS should generally follow:

```text
1. Authentication
2. Clinic isolation
3. Authorization
4. Medical-record protection
5. Document protection
6. Data integrity
7. Auditability
8. Abuse protection
9. Operational security
```

These are all important; the ordering represents implementation focus rather than permission to neglect lower items.

---

# 49. Final Security Principle

IATROS must operate under the assumption that a malicious or incorrectly behaving client can call any API endpoint directly.

Therefore:

**If an operation must not be possible, the backend must make it impossible.**

The final security boundary is:

```text
Authenticated User
        ↓
Role
        ↓
Clinic
        ↓
Resource
        ↓
Authorization
        ↓
Business Rules
        ↓
Database
```

The frontend improves usability.

The backend provides security.
