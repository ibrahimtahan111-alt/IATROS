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

The exact hashing algorithm/configuration will be selected during implementation.

Requirements:

* Password hashes only
* Never log passwords
* Never return password hashes through APIs
* Never include passwords in error messages
* Never commit passwords to Git

---

# 6. Authentication Tokens / Sessions

The exact authentication mechanism will be finalized during implementation.

Regardless of the chosen mechanism:

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

The exact patient fields exposed to secretaries should follow data-minimization principles.

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
403 Forbidden / appropriate protected-resource response
```

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

The exact storage provider will be decided later.

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

---

# 32. CSRF

The CSRF strategy must be determined based on the final authentication/session architecture.

The implementation must not simply disable CSRF protection without evaluating whether the chosen authentication mechanism requires it.

The final decision will be documented when the authentication architecture is finalized.

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

The exact identifier strategy will be finalized in the implementation stage.

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

* Foreign keys
* Unique constraints
* Not-null constraints
* Valid relationships
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
