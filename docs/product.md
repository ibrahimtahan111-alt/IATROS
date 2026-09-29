# IATROS — Product Definition

## 1. Product Overview

**IATROS** is a web-based SaaS clinic management platform designed for **solo doctors and small clinics**.

IATROS provides a centralized system for managing the daily operations of a clinic, including:

* Patients
* Medical records
* Appointments
* Doctor schedules
* Doctors and staff
* Clinic activity
* Key performance indicators
* Clinic settings

The product is designed to provide a simple, reliable, and organized workflow without the complexity of large hospital management systems.

IATROS is intended to be **modular and extensible**, allowing additional medical and specialty-specific functionality to be added in the future without redesigning the core platform.

---

## 2. Product Vision

The vision of IATROS is to provide small medical practices with a modern, accessible, and structured platform for managing their clinic from a single application.

The initial product focuses on **core clinic management** rather than attempting to provide every possible healthcare feature.

Future versions may introduce specialty-specific modules, patient-facing functionality, integrations, advanced analytics, and AI-powered features.

---

## 3. Target Users

### 3.1 Primary Target

IATROS is primarily designed for:

* Solo/private doctors
* Small medical clinics
* Clinics with a small number of doctors and staff

### 3.2 Initial Staff Types

The system supports three operational roles in V1:

1. **Clinic Owner**
2. **Doctor**
3. **Secretary**

A **Patient** role may exist architecturally for future functionality, but patient accounts and the patient portal are not part of V1.

---

## 4. Problems IATROS Solves

Small clinics may need to manage multiple aspects of their daily operations across disconnected systems or manual processes.

IATROS aims to centralize these workflows.

### Core problems addressed

* Managing clinic staff and doctors
* Managing patient information
* Maintaining patient medical records
* Scheduling and managing appointments
* Managing doctor availability and schedules
* Providing clinic activity information
* Providing useful operational KPIs
* Controlling access to clinic information according to staff responsibilities
* Keeping clinic information organized in one system

---

## 5. Core Product Areas

IATROS V1 consists of the following core areas.

### 5.1 Authentication

Users can securely access IATROS through their accounts.

Core functionality includes:

* Login
* Logout
* Password reset
* Password change
* Role-based access
* Account activation/deactivation where applicable

---

### 5.2 Clinic Management

The clinic owner manages the clinic's basic configuration.

This includes:

* Clinic information
* Clinic settings
* Staff
* Doctors
* Doctor specialties
* General operational configuration

IATROS V1 is designed around a clinic-based organizational structure.

---

### 5.3 Staff Management

The clinic owner can manage members of the clinic team.

Staff members can have different roles and corresponding access.

V1 supports:

* Adding team members
* Viewing team members
* Editing team member information
* Activating/deactivating team members
* Assigning supported roles
* Managing doctor-specific information

---

### 5.4 Doctor Management

Doctors are members of a clinic and have doctor-specific information.

A doctor has:

* Basic profile information
* Primary specialty
* Optional additional specialty information
* Working schedule/availability
* Appointments
* Patients they are authorized to access
* Relevant dashboard information

The specialty model is intentionally simple in V1.

A structured primary specialty is supported, while additional specialty information can be represented as text.

Future versions may introduce dedicated modules for individual specialties.

---

### 5.5 Patient Management

Patients are managed by the clinic.

A patient has a central profile containing relevant personal and contact information.

V1 does not require patients to have their own IATROS accounts.

Patients are associated with the clinic rather than being permanently restricted to a single doctor.

A patient may have a primary doctor and may also be associated with other authorized doctors within the clinic.

---

### 5.6 Medical Records

Every patient has a medical record.

The V1 medical record is intentionally limited to the information required for a useful general-purpose clinic system.

It includes:

* Allergies
* Chronic conditions
* Current medications
* Past medical history
* General medical notes
* Consultation history
* Diagnoses
* Clinical/treatment notes
* Follow-up information
* Medical documents/files

The medical record must be designed as a **generic foundation**.

Specialty-specific information should be introduced through future modules rather than forcing specialty-specific structures into the V1 core medical record.

---

### 5.7 Appointments

Appointments are managed primarily through a **calendar-based interface**.

The calendar is the primary appointment-management experience rather than a traditional appointments list.

Appointments support information such as:

* Patient
* Doctor
* Date
* Start time
* End time/duration
* Appointment type
* Status
* Notes
* Reason for visit

The appointment workflow supports:

* Creating appointments
* Editing appointments
* Rescheduling appointments
* Cancelling appointments
* Viewing appointment details
* Managing appointment status
* Preventing conflicting appointments

The appointment system is connected to doctor availability and schedules.

---

### 5.8 Doctor Schedules

Doctors can have defined working schedules and availability.

The scheduling system is used to determine when appointments can be created.

The system should prevent appointment conflicts and respect configured doctor availability.

Future integrations with external calendars such as Google Calendar or Microsoft Outlook may be added later.

---

### 5.9 Dashboards and KPIs

IATROS provides dashboards appropriate to the user's role.

#### Clinic Owner Dashboard

The owner can view clinic-level information, including:

* Clinic activity
* Patient activity
* Appointment activity
* Doctor activity
* Operational KPIs
* Relevant performance information

The owner can view clinic-wide information.

#### Doctor Dashboard

A doctor receives a view focused on their own work.

Potential information includes:

* Today's patients
* Today's appointments
* Their patient distribution
* Their patient list
* Relevant patient/condition information
* Appointment details
* Personal performance information

Doctors must not receive unrestricted access to other doctors' private information simply because they belong to the same clinic.

---

## 6. User Experience Principles

IATROS should follow these principles:

### Simple

Users should be able to understand the system without extensive training.

### Clear

Important information and actions should be easy to identify.

### Consistent

The same interaction patterns should be used throughout the application.

### Role-focused

Users should primarily see information and actions relevant to their responsibilities.

### Efficient

Common clinic tasks should require as few unnecessary steps as possible.

### Reliable

The system should prioritize data integrity and prevent common operational errors such as scheduling conflicts and unauthorized access.

---

## 7. Localization

IATROS V1 supports:

* English
* French

The application must be designed for localization from the beginning rather than translating the interface manually after development.

All user-facing text should be externalized and translation-ready.

---

## 8. Appearance

IATROS supports:

* Light mode
* Dark mode

The interface should maintain consistent usability and readability in both modes.

---

## 9. V1 Scope

The initial release focuses on the core clinic-management workflow.

### Included in V1

* Authentication
* Clinic setup and settings
* Staff management
* Doctor management
* Roles and access control
* Patient management
* Patient medical records
* Calendar-based appointment management
* Doctor schedules and availability
* Owner dashboard
* Doctor dashboard
* Clinic activity and KPIs
* Search
* Filtering
* Sorting
* English/French localization
* Light/dark mode

---

## 10. Explicitly Outside V1

The following are not part of the initial implementation unless explicitly added later:

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

These features may be considered in future versions.

---

## 11. Future Product Direction

IATROS is intended to grow through modular expansion.

The long-term direction may include specialty-specific modules such as:

* Dentistry
* Dermatology
* Cardiology
* Ophthalmology
* Other medical specialties

These modules should build on the existing IATROS foundation rather than creating independent patient-management systems.

Potential future platform capabilities include:

* Patient portal
* Patient accounts
* External calendar integrations
* Notifications
* SMS/WhatsApp communication
* Billing and payments
* Advanced analytics
* Mobile applications
* AI-assisted functionality

Future functionality must not compromise the simplicity and reliability of the core clinic-management experience.

---

## 12. Product Architecture Principle

IATROS should be designed as a **modular clinic-management platform**, not as a collection of unrelated features.

The core entities and workflows should remain reusable as the product expands.

The architecture should therefore support:

* Multiple users within a clinic
* Multiple doctors within a clinic
* Patients associated with a clinic
* Patients associated with multiple authorized doctors
* Role-based access
* Extensible medical records
* Extensible specialties
* Future patient accounts
* Future specialty modules

---

## 13. Source of Truth

This document defines the high-level product direction for IATROS.

The other project documentation must remain consistent with this definition.

If a future requirement conflicts with this document, the product documentation must be reviewed and updated before the conflicting functionality is implemented.

No feature should be added solely because it is technically possible.

IATROS development should prioritize:

1. Core clinic workflow
2. Data integrity
3. Security and access control
4. Usability
5. Maintainability
6. Extensibility
7. Future functionality
