# IATROS — Database Design

## 1. Purpose

This document defines the database architecture and domain model for IATROS V1.

IATROS uses **PostgreSQL** as its relational database.

The database must prioritize:

* Data integrity
* Security
* Clear relationships
* Auditability
* Maintainability
* Appropriate normalization
* Future extensibility

The database is an implementation of the product and requirements defined in the other IATROS documentation.

---

# 2. Database Principles

## 2.1 Relational Model

IATROS uses a relational database.

Relationships between entities must be represented explicitly through appropriate foreign keys and constraints.

## 2.2 Clinic-Based Data Isolation

The clinic is the primary organizational boundary.

Clinic-owned entities must be associated with the appropriate clinic.

The backend must verify clinic ownership/access before allowing data to be retrieved or modified.

## 2.3 Data Integrity

The database should enforce fundamental integrity rules through:

* Primary keys
* Foreign keys
* Unique constraints
* Not-null constraints where appropriate
* Check constraints where appropriate
* Appropriate indexes

Application-level validation must complement, not replace, database integrity.

## 2.4 Avoid Unnecessary Duplication

Data should not be duplicated unnecessarily.

For example, a patient's clinic should be derived from the patient's clinic relationship rather than repeatedly copied into unrelated records unless there is a specific architectural reason.

## 2.5 Historical Data

Clinical and operational history should be preserved where required.

Records such as consultations and completed/cancelled appointments must not be silently overwritten or removed simply because they are no longer active.

---

# 3. Core Entity Model

The initial domain consists of:

```text
Clinic
│
├── User
│    ├── Owner
│    ├── Doctor
│    └── Secretary
│
├── Doctor
│    └── Specialty
│
├── Patient
│    ├── Medical Record
│    │    ├── Consultations
│    │    └── Documents
│    │
│    └── Doctor Associations
│
├── Appointment
│
├── Doctor Schedule
│
└── Clinic Settings
```

The exact physical schema may contain additional supporting tables.

---

# 4. Clinic

The `clinic` entity represents a clinic using IATROS.

### Core fields

* `id`
* `name`
* `email` where applicable
* `phone` where applicable
* `address` where applicable
* `created_at`
* `updated_at`
* `status`

### Relationships

A clinic may have:

* Many users
* Many doctors
* Many patients
* Many appointments
* Many schedules
* Clinic settings

A clinic must not access data belonging to another clinic.

---

# 5. User

The `user` entity represents an authenticated IATROS account.

A user belongs to one clinic in V1.

### Core fields

* `id`
* `clinic_id`
* `first_name`
* `last_name`
* `email`
* `password_hash`
* `role`
* `status`
* `created_at`
* `updated_at`
* `last_login_at` where required

### Roles

V1 supports:

* `OWNER`
* `DOCTOR`
* `SECRETARY`

A future `PATIENT` role may be introduced.

### Constraints

* Email must be unique within the appropriate account scope.
* Passwords must never be stored in plaintext.
* Deactivated users must not be able to authenticate.

---

# 6. Doctor

A doctor is a clinic staff member with doctor-specific information.

The doctor domain should have a relationship to the authenticated user rather than duplicating authentication information.

Conceptually:

```text
User
 │
 └── Doctor Profile
```

### Core fields

* `id`
* `user_id`
* `clinic_id`
* `primary_specialty_id`
* `additional_specialty`
* `created_at`
* `updated_at`

### Specialty

Each doctor has:

* One primary specialty
* Optional additional specialty text

The additional specialty field is intentionally free-form in V1.

Future specialty modules may introduce more structured specialty-specific functionality.

---

# 7. Specialty

The `specialty` entity contains the structured list of primary specialties supported by IATROS.

### Core fields

* `id`
* `name`
* `description` where required
* `active`
* `created_at`
* `updated_at`

The specialty list should be manageable through controlled application data rather than arbitrary user input.

Specialties should be identifiable by stable IDs rather than relying on their display names.

---

# 8. Patient

A patient represents a person receiving care from the clinic.

A patient belongs to one clinic.

### Core fields

* `id`
* `clinic_id`
* `first_name`
* `last_name`
* `date_of_birth`
* `gender` where required
* `phone`
* `email`
* `address` where required
* `emergency_contact` where required
* `primary_doctor_id` where applicable
* `created_at`
* `updated_at`
* `status`

The application should only collect patient information necessary for the intended functionality.

---

# 9. Patient-Doctor Association

Patients must not be modeled as permanently belonging to only one doctor.

A separate association is required to support multiple authorized doctors.

Conceptually:

```text
Patient
   │
   ├── Primary Doctor
   │
   └── Authorized Doctors
          ├── Doctor A
          └── Doctor B
```

A many-to-many relationship should be used where appropriate.

A supporting relationship entity may contain:

* `id`
* `patient_id`
* `doctor_id`
* `relationship_type`
* `created_at`
* `updated_at`

Possible relationship types include:

* `PRIMARY`
* `AUTHORIZED`

The exact implementation should prevent duplicate associations.

---

# 10. Medical Record

Each patient has one core medical record.

Conceptually:

```text
Patient
   │
   └── Medical Record
```

### Core fields

* `id`
* `patient_id`
* `created_at`
* `updated_at`

General medical information may be represented through appropriately structured fields or related entities.

The medical record must not become a single unrestricted text field containing all patient information.

---

# 11. Medical Conditions

Chronic conditions and relevant medical conditions should be represented in a structured way where appropriate.

A condition record may contain:

* `id`
* `medical_record_id`
* `name`
* `description` where required
* `status`
* `diagnosed_at` where available
* `created_at`
* `updated_at`

This allows conditions to be individually managed instead of storing everything in one text field.

---

# 12. Allergies

Allergies should be represented as individual records where appropriate.

An allergy may contain:

* `id`
* `medical_record_id`
* `allergen`
* `reaction` where available
* `severity` where available
* `notes` where required
* `created_at`
* `updated_at`

The V1 implementation should remain simple.

---

# 13. Medications

Current medications may be represented as individual records.

A medication may contain:

* `id`
* `medical_record_id`
* `name`
* `dosage` where available
* `frequency` where available
* `notes` where required
* `start_date` where available
* `end_date` where available
* `active`
* `created_at`
* `updated_at`

The V1 medication model is informational.

It is not a full electronic prescribing system.

---

# 14. Medical History

Past medical history may be stored as structured records where appropriate.

A history record may contain:

* `id`
* `medical_record_id`
* `description`
* `date` where available
* `notes`
* `created_at`
* `updated_at`

The model should remain generic enough for future expansion.

---

# 15. Medical Notes

General medical notes may be associated with the medical record.

Notes should contain:

* `id`
* `medical_record_id`
* `content`
* `created_by`
* `created_at`
* `updated_at`

The author of a medical note must be identifiable.

---

# 16. Consultation

A consultation represents a clinical encounter.

Consultations form the historical clinical timeline of a patient.

### Core fields

* `id`
* `medical_record_id`
* `patient_id`
* `doctor_id`
* `appointment_id` where applicable
* `date_time`
* `reason`
* `diagnosis`
* `clinical_notes`
* `treatment_notes`
* `follow_up_notes`
* `created_at`
* `updated_at`

A consultation must preserve historical information.

Updating a consultation must not unintentionally modify unrelated historical consultations.

---

# 17. Appointment

An appointment represents a scheduled interaction between a patient and a doctor.

### Core fields

* `id`
* `clinic_id`
* `patient_id`
* `doctor_id`
* `appointment_date`
* `start_time`
* `end_time`
* `appointment_type`
* `status`
* `reason`
* `notes`
* `created_by`
* `created_at`
* `updated_at`

### Status values

V1 supports:

* `SCHEDULED`
* `CONFIRMED`
* `WAITING`
* `IN_CONSULTATION`
* `COMPLETED`
* `CANCELLED`
* `NO_SHOW`

The database should use stable internal values rather than translated display text.

---

# 18. Appointment Integrity

The database and backend must work together to prevent conflicting appointments.

At minimum, the system must prevent two active appointments from occupying the same doctor's time.

Conflict detection must account for:

* Date
* Start time
* End time
* Doctor
* Appointment status

Cancelled appointments should not block future availability.

The final conflict-prevention implementation must be defined in the backend architecture and database implementation.

---

# 19. Doctor Schedule

A doctor schedule defines the doctor's normal working availability.

A schedule record may contain:

* `id`
* `doctor_id`
* `day_of_week`
* `start_time`
* `end_time`
* `active`
* `created_at`
* `updated_at`

The model should allow multiple working periods for a doctor on the same day.

Example:

```text
Monday
├── 09:00–12:00
└── 14:00–17:00
```

---

# 20. Schedule Exceptions

The system may require exceptions to normal schedules.

Examples include:

* Vacation
* Holiday
* One-time absence
* Special working day

The V1 model should support schedule exceptions if required by the appointment workflow.

A schedule exception may contain:

* `id`
* `doctor_id`
* `date`
* `start_time`
* `end_time`
* `type`
* `reason`
* `created_at`
* `updated_at`

The exact implementation may be finalized during appointment/schedule implementation.

---

# 21. Clinic Settings

Clinic-level configuration should be separated from the primary clinic identity record where appropriate.

Potential settings include:

* Default appointment duration
* Clinic preferences
* Localization preferences
* Time zone
* Other operational configuration

The settings model should not become a generic unstructured key-value table unless a concrete requirement justifies that approach.

---

# 22. Documents

Medical documents may be associated with a patient's medical record.

A document record should contain metadata such as:

* `id`
* `medical_record_id`
* `file_name`
* `file_type`
* `file_size`
* `storage_reference`
* `uploaded_by`
* `created_at`

The actual file storage mechanism is separate from the relational metadata.

Access to a document must follow the same authorization rules as the associated medical record.

---

# 23. Audit Records

Sensitive operations should be auditable.

An audit record may contain:

* `id`
* `clinic_id`
* `user_id`
* `action`
* `entity_type`
* `entity_id`
* `timestamp`
* `metadata` where appropriate

Audit logs must not unnecessarily store sensitive medical information.

The audit model should support tracing important actions to the responsible user.

---

# 24. Password Reset Data

Password-reset functionality may require temporary security records.

Such records should contain appropriate information such as:

* User reference
* Secure reset token representation
* Expiration
* Usage status
* Creation timestamp

Reset tokens must not be stored or logged in plaintext where avoidable.

Expired or consumed reset mechanisms must not remain usable.

---

# 25. Entity Relationships

The primary relationships are:

```text
Clinic
 │
 ├── Users
 │     ├── Owner
 │     ├── Doctors
 │     └── Secretaries
 │
 ├── Doctors
 │     ├── Primary Specialty
 │     ├── Schedule
 │     └── Patient Associations
 │
 ├── Patients
 │     ├── Medical Record
 │     │     ├── Conditions
 │     │     ├── Allergies
 │     │     ├── Medications
 │     │     ├── Medical History
 │     │     ├── Notes
 │     │     ├── Consultations
 │     │     └── Documents
 │     │
 │     └── Doctor Associations
 │
 └── Appointments
        ├── Patient
        └── Doctor
```

---

# 26. Primary Keys

All major entities should use stable primary keys.

The implementation should use a consistent ID strategy across the application.

The chosen ID type must be compatible with:

* PostgreSQL
* JPA/Hibernate
* API serialization
* Future scaling

The final ID-generation strategy must be consistently applied throughout the database.

---

# 27. Timestamps

Persistent entities should generally contain:

* `created_at`
* `updated_at`

Where historical tracking is important, additional timestamps should be used.

Examples:

* Appointment completion time
* Consultation time
* Account deactivation time
* Document upload time

All timestamps must use a consistent timezone strategy.

The application should store timestamps in a canonical format and convert them appropriately for display.

---

# 28. Soft Deletion

Important clinical and operational records should not be physically deleted without a deliberate policy.

Where deletion is required by the product, the implementation should distinguish between:

* Deactivation
* Archiving
* Permanent deletion

Medical and historical records should generally favor preservation over destructive deletion.

The final deletion policy must be defined for each entity before destructive functionality is implemented.

---

# 29. Indexing

Indexes must be created for fields frequently used in:

* Authentication
* Foreign-key lookups
* Search
* Filtering
* Sorting
* Appointment queries
* Doctor schedules
* Patient lookup
* Clinic isolation

Potential indexed fields include:

* User email
* Clinic ID
* Patient name/search fields
* Doctor ID
* Appointment date/time
* Appointment doctor
* Appointment patient
* Medical record patient
* Consultation patient/doctor
* Schedule doctor/date

Indexes should be introduced based on actual query patterns rather than indiscriminately indexing every field.

---

# 30. Pagination

Large collections must support pagination.

The database/API layer should support pagination for collections such as:

* Patients
* Staff
* Doctors
* Appointments
* Consultations
* Documents
* Audit records

Pagination must be applied after authorization scope has been established.

The client must not download an unlimited collection simply to perform pagination locally.

---

# 31. Search

Search must be designed around the actual fields users need to locate records.

For example, patient search may include:

* First name
* Last name
* Phone
* Email
* Other approved identifiers

Search must respect authorization and clinic boundaries.

---

# 32. Database Transactions

Operations involving multiple related records should use appropriate database transactions.

Examples include:

* Creating a patient and medical record
* Creating an appointment and related associations
* Changing sensitive staff information
* Updating multiple related medical-record records

A failed transactional operation must not leave partially completed data.

---

# 33. Database Migrations

All database schema changes must be version-controlled through migrations.

Developers must not depend on manually changing the production schema.

Each migration must be:

* Reproducible
* Version-controlled
* Ordered
* Reviewable

The exact migration framework will be selected as part of the implementation tooling.

---

# 34. Future Specialty Modules

The database must support future specialty-specific modules without modifying the fundamental patient identity model.

The intended structure is:

```text
Core Patient
      │
      └── Core Medical Record
              │
              └── Specialty Module
```

A future specialty module should add its own domain entities where appropriate.

For example, a future dental module may contain dental-specific information without adding dental-specific columns to the generic patient table.

---

# 35. Future Patient Accounts

Patient accounts are not implemented in V1.

The database architecture should allow a future patient account to be associated with an existing patient record.

The future implementation must not require creating a duplicate patient simply because the patient gains an account.

---

# 36. Database Security

The application must never expose database credentials to the frontend.

Database credentials must be stored securely in backend environment configuration.

The frontend must communicate only with authorized backend APIs.

Medical data must not be directly accessible through public database connections.

---

# 37. Database Design Rule

When introducing a new entity or relationship:

1. Determine whether the entity is genuinely required.
2. Check whether an existing entity already represents the concept.
3. Define its ownership and authorization scope.
4. Define its relationships.
5. Define required constraints.
6. Define indexing requirements.
7. Consider historical integrity.
8. Consider future extensibility.
9. Update this document before implementing the schema.

Avoid creating database structures merely to support hypothetical future functionality.

---

# 38. Source of Truth

This document defines the intended V1 database/domain model.

The implementation must remain consistent with:

* `product.md`
* `requirements.md`
* `roles-and-permissions.md`
* `architecture.md`

If a database requirement conflicts with another document, the documentation must be reviewed before implementation.

The database must not become an accidental source of product requirements.
