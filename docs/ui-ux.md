# IATROS — UI/UX Specification

## 1. Purpose

This document defines how users interact with IATROS.

It covers:

* Application navigation
* Page structure
* Screens
* Cards
* Tables
* Forms
* Buttons
* Modals
* Search
* Filtering
* Sorting
* Pagination
* Calendar interactions
* Patient workflows
* Medical-record workflows
* Appointment workflows
* Role-specific experiences
* Loading, empty, success, and error states
* Responsive behavior
* Accessibility
* English/French support
* Light/dark mode

The objective is to provide a clear interface specification before implementation begins.

---

# 2. UX Principles

IATROS should feel like a professional clinic-management application, not a generic administration dashboard.

The interface should prioritize:

1. **Clarity**
2. **Speed**
3. **Consistency**
4. **Minimal unnecessary steps**
5. **Data safety**
6. **Easy navigation**
7. **Strong visual hierarchy**
8. **Responsive behavior**
9. **Predictable interactions**

The user should generally understand:

* where they are;
* what they are looking at;
* what they can do;
* what action is primary;
* what will happen when they click an action.

---

# 3. Application Layout

The authenticated application uses the IATROS **workspace layout** defined in `design-system.md` §4: a navigation rail, a context bar, the main workspace, and an optional context panel. It deliberately avoids the generic "sidebar + header + cards" admin template.

```text
┌────┬──────────────────────────────────────────────┬──────────────┐
│    │  Context bar: page title · scope · actions    │              │
│ R  ├──────────────────────────────────────────────┤   Context    │
│ a  │                                              │    panel     │
│ i  │              Main workspace                  │  (optional)  │
│ l  │                                              │              │
└────┴──────────────────────────────────────────────┴──────────────┘
```

## 3.1 Navigation rail

The rail contains the clinic name, the primary navigation, and the user menu.

Items depend on the role (§38). Items that the current user cannot access must not be displayed.

The rail collapses to icons on tablets and becomes a bottom bar on small screens.

---

# 4. Top Navigation

There is no generic top header. Each page has a **context bar** (`design-system.md` §4.2) containing:

* Page title
* Page scope controls (date navigation, doctor selector, view switch)
* The page's single primary action

## User menu

The user menu sits at the bottom of the navigation rail and contains:

* My account (profile and password)
* Language (English / Français)
* Theme (Light / Dark / System)
* Log out

Additional account options may be added later.

---

# 5. Global UX Components

IATROS should use reusable components throughout the application.

## 5.1 Buttons

Primary actions:

* Add
* Create
* Save
* Confirm
* Book
* Update

Secondary actions:

* Cancel
* Close
* Back
* Reset

Danger actions:

* Delete
* Deactivate
* Cancel Appointment
* Remove

Buttons should use consistent placement and terminology.

---

# 6. Global Search / Filter / Sort / Pagination

Search, filtering, and sorting are cross-application UX requirements.

Whenever a page contains a dataset where these controls are useful, the page should provide them.

## Search

Search should:

* be clearly visible;
* have an understandable placeholder;
* search only authorized data;
* support server-side search for large datasets;
* provide clear feedback when there are no results.

Example:

```text
[ 🔍 Search patients... ]
```

## Filters

Filters should be contextual to the page.

Examples:

* Doctor
* Date
* Status
* Specialty
* Appointment type
* Patient

## Sorting

Tables should allow sorting of relevant columns.

Example:

```text
Patient Name ↑
Date ↓
Status
Doctor
```

## Pagination

Every table that can contain many records must use pagination.

Example:

```text
Showing 21–40 of 137

[ Previous ] [ 1 ] [ 2 ] [ 3 ] ... [ 7 ] [ Next ]
```

The frontend must not retrieve an unlimited number of records simply to display a paginated table.

---

# 7. Dashboard — Owner

## Purpose

Provide a quick overview of clinic activity.

## Page structure

### Header

```text
Dashboard

Good morning, [Name]

[ Date / period selector ]
```

### KPI cards

Example cards:

* Today's Appointments
* Completed Today
* Waiting
* No-Shows
* Total Patients
* Active Doctors

Cards should be clickable where a useful detailed view exists.

### Activity section

Possible sections:

* Today's appointments
* Recent patient activity
* Doctor activity
* Recent clinic events

### Performance section

Displays relevant clinic-level KPIs.

Examples:

* Appointment trends
* Completion rate
* No-show activity
* Doctor activity

The exact KPI calculations belong to the KPI requirements/API definition.

**Implementation (`decisions.md` D-039):** the header shows the greeting and today's date in the clinic's time zone. Tiles: today's appointments, completed, waiting, no-shows, total (active) patients, and active doctors; the appointment tiles open the calendar's day view. Below: today's schedule (each row opens the appointment in the calendar), each doctor's day, a performance section with its own period selector (week, month, quarter, year) linking to the activity page, and the latest clinic activity. The dashboard refreshes every minute.

---

# 8. Dashboard — Doctor

## Purpose

Give a doctor a focused view of their own work.

## Header

```text
Dashboard

Good morning, Dr. [Name]
```

## KPI cards

Examples:

* Today's Patients
* Today's Appointments
* Upcoming Appointments
* Completed Consultations

## Today's schedule

A compact appointment timeline/list.

Each appointment may show:

* Time
* Patient
* Appointment type
* Status

## Patient section

Shows relevant patients.

Only patients the doctor is authorized to access may appear.

## Doctor activity

Displays the doctor's own relevant activity/KPIs.

The doctor must not see unrestricted clinic-wide information.

**Implementation (`decisions.md` D-039):** tiles for today's patients, today's appointments, upcoming appointments (next 7 days), and completed today. The schedule marks severe allergies and active chronic conditions of linked patients. The patient section shows the doctor's patient counts and the five most recently seen patients. Performance shows the doctor's own KPIs, and the activity list shows only their own activity. An owner with a doctor profile uses the owner dashboard; their own figures are on the activity page filtered to themselves.

---

# 9. Calendar Page

## Purpose

The calendar is the primary appointment-management interface.

It should not be replaced by an appointments table as the main workflow.

## Header

```text
Calendar

[ Today ] [ < ] [ > ]

[ Day ] [ Week ] [ Month ]

[ Doctor ▼ ]
```

## Calendar views

### Day

Displays appointments chronologically.

### Week

Displays each day and appointments within the doctor's working periods.

### Month

Displays appointment distribution across the month.

## Appointment card

An appointment should visually communicate:

* Patient
* Time
* Doctor where relevant
* Appointment type
* Status

## Calendar actions

Clicking an empty time slot:

→ Open Create Appointment modal.

Clicking an appointment:

→ Open Appointment Details.

From appointment details:

* Edit
* Reschedule
* Change status
* Cancel
* View patient
* Start/view consultation where applicable

## Conflict prevention

The UI should provide immediate feedback when a booking conflicts with:

* another appointment;
* unavailable doctor time;
* invalid schedule.

The backend remains authoritative.

## Implementation (D-038)

* View, date, doctor filter, the open appointment, and "Show cancelled" live in the URL (`/calendar?view=day&date=2026-10-12&doctor=…&appointment=…&cancelled=1`), so a refresh or a shared link reopens the same place. Week is the default view and weeks start on the clinic's week start day.
* Day view shows one column per doctor; week view shows one column per date, with each event naming its doctor when several are shown. Time outside working hours is shaded and a Now line marks the current time. Overlapping appointments sit side by side.
* Month view shows up to three appointments per day plus "+N more"; a day opens its day view.
* The doctor filter is offered to owners and secretaries; doctors only ever see their own calendar.
* Appointment details open in a right-side panel with the actions the server allows. Start consultation appears for the appointment's doctor once the patient is waiting, in consultation, or completed, and links the consultation to the appointment.
* Booking offers the doctor's free start times from the availability API; a refused booking keeps everything entered and explains why.
* The patient profile lists the patient's appointments (latest first) with booking, and the patients list shows each patient's next appointment.

---

# 10. Create Appointment Modal

## Fields

* Patient
* Doctor
* Date
* Start time
* Duration
* Appointment type
* Reason for visit
* Notes

## Actions

Primary:

**Book Appointment**

Secondary:

**Cancel**

## Validation

The form should validate:

* required fields;
* valid date/time;
* doctor availability;
* appointment conflicts.

If a conflict exists:

```text
This doctor is not available at this time.
```

The user should be able to modify the time without losing unrelated form information.

---

# 11. Appointment Details

The details panel/modal displays:

* Patient
* Doctor
* Date
* Time
* Duration
* Appointment type
* Status
* Reason
* Notes
* Created by
* Relevant timestamps

## Actions

Depending on role and appointment state:

* Edit
* Reschedule
* Change status
* Cancel
* Open Patient
* Open Medical Record
* Start Consultation

Historical information should remain visible.

---

# 12. Patients Page

## Purpose

Provide the main patient-management interface.

## Header

```text
Patients

[ Search patients... ]

[ Filters ]

[ Add Patient ]
```

## Patient table

Columns:

* Patient
* Date of Birth / Age
* Phone
* Primary Doctor
* Last Appointment
* Status
* Actions

The exact columns may be adjusted during implementation for usability.

## Table actions

Each row may provide:

* View
* Edit
* Open Medical Record
* Book Appointment

## Pagination

Required.

## Search/filter/sort

Required.

Possible filters:

* Primary doctor
* Patient status
* Recent activity

---

# 13. Add Patient

The form should collect the minimum necessary patient information.

## Sections

### Personal Information

* First name
* Last name
* Date of birth
* Gender where required
* Phone
* Email where available
* Address where required

### Clinic Information

* Primary doctor
* Additional authorized doctors where applicable

### Medical Information

Basic information may be entered here where appropriate, but detailed clinical information belongs in the Medical Record.

## Actions

* Create Patient
* Cancel

After successful creation:

→ Open Patient Profile.

---

# 14. Patient Profile

The patient profile is the central hub for a patient.

## Header

```text
[Patient Name]

Age: XX
Primary Doctor: Dr. X

[ Edit Patient ] [ Book Appointment ]
```

## Main sections

* Overview
* Medical Record
* Appointments
* Consultations
* Documents

## Overview

May display:

* Personal information
* Contact information
* Primary doctor
* Authorized doctors
* Allergies summary
* Chronic conditions summary
* Recent activity

Access remains role-controlled.

---

# 15. Medical Record Page

## Purpose

Provide a structured view of the patient's medical information.

## Header

```text
Medical Record

Patient Name
Primary Doctor

[ Edit Record ]
```

## Sections

### Allergies

Displays known allergies.

### Chronic Conditions

Displays relevant conditions.

### Current Medications

Displays current medications.

### Past Medical History

Displays historical information.

### General Medical Notes

Displays relevant general notes.

### Consultation History

Displays clinical encounters chronologically.

### Documents

Displays associated medical documents, newest first, with search by file name and pagination. Each row shows the name (opens the file in a new tab), size, who added it, and when, with a download action and, for the uploader or the owner, an archive action behind a confirmation. "Upload document" opens the file picker; unsupported or oversized files are explained before any upload, and the server's validation is shown in the section. The consultation page shows the same section limited to that consultation's attachments, and uploads from there are attached to it. On archived patients the section is read-only (D-044).

---

# 16. Consultation History

Consultations should be displayed in chronological order.

Example:

```text
2027-04-12
Dr. Smith
Reason: Follow-up

Diagnosis
...

Clinical Notes
...

Follow-up
...
```

The user can select a consultation to open its complete details.

## Actions

Authorized doctors may:

* Create consultation
* Edit permitted information
* View consultation

Owner access follows the established owner permissions.

Secretary has no clinical consultation access.

---

# 17. Create Consultation

A consultation may be started from:

* Appointment details
* Patient profile
* Medical record

## Fields

* Patient
* Doctor
* Date/time
* Appointment reference
* Reason for visit
* Diagnosis
* Clinical notes
* Treatment notes
* Follow-up information
* Documents

## Actions

* Save Consultation
* Cancel

The consultation becomes part of the patient's medical history.

---

# 18. Doctor / Team Page

The owner uses this area to manage clinic staff and doctors.

## Header

```text
Team

[ Search team members... ]

[ Filters ]

[ Add Team Member ]
```

## Table

Columns:

* Name
* Role
* Specialty
* Status
* Contact
* Actions

## Actions

* View
* Edit
* Deactivate
* Reactivate where applicable

Search/filter/sort/pagination are required.

---

# 19. Add Team Member

## Step 1 — Basic Information

* First name
* Last name
* Email
* Phone

## Step 2 — Role

Options:

* Doctor
* Secretary

The clinic owner is not created through ordinary team-member creation.

## Step 3 — Doctor Information

If role = Doctor:

* Primary specialty
* Additional specialty
* Professional information

## Step 4 — Schedule

Schedule configuration may be completed immediately or later.

## Actions

* Create
* Cancel

---

# 20. Doctor Profile

The doctor profile displays:

* Name
* Specialty
* Additional specialty
* Contact information
* Status
* Schedule summary
* Upcoming appointments
* Relevant authorized patient information

Owner and doctor views must respect permissions.

---

# 21. Doctor Schedules Page

## Purpose

Manage doctor availability.

## Header

```text
Doctor Schedules

[ Doctor ▼ ]

[ Edit Schedule ]
```

## Weekly schedule

Example:

```text
Monday       09:00–12:00   14:00–18:00
Tuesday      09:00–12:00   14:00–18:00
Wednesday    09:00–13:00
Thursday     09:00–12:00   14:00–18:00
Friday       09:00–14:00
Saturday     Closed
Sunday       Closed
```

## Actions

* Add working period
* Edit working period
* Remove working period
* Add exception
* Save schedule

The interface should make overlapping periods difficult or impossible to create.

---

# 22. Activity / KPI Page

## Owner view

The owner can see clinic-wide activity.

## Filters

Possible filters:

* Date range
* Doctor
* Appointment status
* Appointment type

## KPI cards

Examples:

* Total appointments
* Completed
* Cancelled
* No-show
* Active patients
* Doctor activity

## Charts

Charts may be used when they communicate trends more effectively than tables.

Avoid unnecessary charts.

## Doctor view

Doctors see only their own permitted activity and KPIs.

**Implementation (`decisions.md` D-039):** filters are the period (week, month, quarter, year, ending today) and, for the owner, the doctor; status and type breakdowns are shown as figures rather than filters, since they are outcomes of the period. Sections: KPI tiles with comparison to the previous period, the appointment trend, a per-doctor table (owner, whole clinic; selecting a doctor applies the doctor filter), and the paginated activity feed filtered by activity type. Filters live in the URL so the view can be shared and survives a reload. Secretaries do not see this page.

---

# 23. Settings Page

Settings should be organized into clear sections.

Possible sections:

### Clinic Information

* Clinic name
* Address
* Contact details

### Staff / Access

Relevant owner-only settings.

### Preferences

* Language
* Theme

### Account

* Profile
* Password

The exact settings hierarchy can be refined during implementation.

---

# 24. Authentication Screens

## Login

Fields:

* Email
* Password

Actions:

* Login
* Forgot password

Optional:

* Show/hide password

## Forgot Password

Fields:

* Email

Action:

* Send reset link

## Reset Password

Fields:

* New password
* Confirm password

Action:

* Reset password

After successful reset:

→ Redirect to Login.

---

# 25. Global Modal Rules

Modals should be used for focused actions that do not require an entire page.

Good modal use cases:

* Create appointment
* Quick edit
* Confirmation
* Short forms
* Status changes

Avoid placing very large or complex workflows inside small modals.

For complex clinical workflows, use a dedicated page or larger panel.

---

# 26. Confirmation Dialogs

Confirmation should be required for consequential actions.

Example:

```text
Deactivate Dr. Smith?

They will no longer be able to access the clinic.

[ Cancel ] [ Deactivate ]
```

The confirmation should clearly describe the consequence.

Avoid unnecessary confirmation dialogs for harmless actions.

---

# 27. Notifications / Feedback

IATROS should provide clear feedback after actions.

## Success

Examples:

```text
Patient created successfully.
```

```text
Appointment booked successfully.
```

## Error

Examples:

```text
We couldn't save the appointment.
Please check the highlighted fields.
```

## Warning

Examples:

```text
This doctor already has an appointment during this time.
```

Feedback should be concise and actionable.

---

# 28. Loading States

Pages should not appear broken while data is loading.

Use appropriate:

* Skeletons
* Loading indicators
* Disabled states

Buttons that trigger requests should indicate progress.

Example:

```text
[ Saving... ]
```

instead of allowing repeated submissions.

---

# 29. Empty States

Every major data-driven page should define an empty state.

Example:

```text
No patients found.

Add your first patient to start managing
your clinic's patient records.

[ Add Patient ]
```

Search with zero results should be differentiated from genuinely empty datasets.

Example:

```text
No patients match "John Smith".

[ Clear Search ]
```

---

# 30. Error States

Errors should be understandable to users.

Avoid displaying:

* Stack traces
* Database errors
* Internal exception names
* Technical implementation details

Example:

```text
Something went wrong.

We couldn't load the patients right now.

[ Try Again ]
```

---

# 31. Form UX

Forms should:

* Clearly identify required fields.
* Validate fields close to where the error occurs.
* Preserve entered information when possible.
* Prevent accidental duplicate submission.
* Clearly distinguish editable and read-only information.
* Use consistent field labels.
* Use appropriate input controls.

Validation should occur on both frontend and backend.

---

# 32. Destructive Actions

Destructive actions should be visually distinct.

Examples:

* Delete
* Deactivate
* Cancel appointment
* Remove document

Where possible, IATROS should prefer reversible operations such as deactivation or archival rather than destroying historical records.

The exact deletion policy is defined at the data/architecture level.

---

# 33. Responsive Design

IATROS is primarily a web application but must support different screen sizes.

## Desktop

Primary experience.

The full sidebar and multi-column layouts may be used.

## Tablet

* Collapsible sidebar
* Responsive cards
* Adapted tables

## Mobile web

* Compact navigation
* Stacked cards
* Horizontally scrollable data tables where necessary
* Calendar adapted to smaller screens
* Large touch targets

The V1 product does not require a native mobile application.

---

# 34. Accessibility

IATROS should follow practical accessibility principles.

Requirements include:

* Keyboard navigation
* Visible focus states
* Accessible labels
* Sufficient contrast
* Semantic HTML
* Form error associations
* Accessible dialogs
* Screen-reader-friendly controls where applicable

Icons should not be the only way to communicate meaning.

---

# 35. Internationalization

IATROS supports:

* English
* French

All user-facing text should come from translation resources rather than being hard-coded throughout components.

This includes:

* Buttons
* Labels
* Validation messages
* Empty states
* Error messages
* Navigation
* Status labels
* Notifications
* Modal text

The application should be designed so additional languages can be added later.

---

# 36. Theme Support

IATROS supports:

* Light mode
* Dark mode

Components must work correctly in both themes.

Theme-specific styling should not be scattered randomly throughout components.

The application should use a consistent design-token approach.

---

# 37. Role-Specific UX

The same underlying application should provide different experiences based on role.

### Owner

Primary focus:

**Managing the clinic**

The owner sees:

* Clinic dashboard
* Staff
* Doctors
* Patients
* Calendar
* Schedules
* KPIs
* Settings

### Doctor

Primary focus:

**Managing their patients and clinical work**

The doctor sees:

* Personal dashboard
* Own calendar
* Authorized patients
* Medical records
* Consultations
* Own schedule
* Own KPIs

### Secretary

Primary focus:

**Managing bookings and appointments**

The secretary sees:

* Calendar
* Patient information required for booking
* Appointment workflows

The secretary does not receive unrestricted access to clinical information.

---

# 38. Navigation Rules by Role

## Owner

```text
Overview        /dashboard
Calendar        /calendar
Patients        /patients
Team            /team
Schedules       /schedules
Activity        /activity
Settings        /settings
```

## Doctor

```text
My day          /dashboard
Calendar        /calendar
Patients        /patients
My schedule     /schedules
My activity     /activity
```

## Secretary

```text
Calendar        /calendar
Patients        /patients
```

Every role reaches `/account` (profile, password, language, theme) through the user menu.

Landing page after login: owner and doctor → `/dashboard`; secretary → `/calendar` (`design-system.md` §5).

Inaccessible modules must never appear as usable navigation options.

---

# 39. Patient Workflow

The main patient workflow should be:

```text
Patients
   ↓
Search / Filter
   ↓
Patient Profile
   ↓
Medical Record
   ├── Overview
   ├── Consultations
   └── Documents
```

Appointment workflow:

```text
Patient Profile
   ↓
Book Appointment
   ↓
Calendar
   ↓
Appointment
   ↓
Consultation
   ↓
Medical Record
```

This creates a natural relationship between operational and clinical workflows.

---

# 40. Appointment Workflow

Standard workflow:

```text
Calendar
   ↓
Select Time
   ↓
Create Appointment
   ↓
Validate Doctor Availability
   ↓
Book
   ↓
Scheduled
   ↓
Confirmed
   ↓
Waiting
   ↓
In Consultation
   ↓
Completed
```

Alternative terminal states:

```text
Cancelled
No-show
```

The UI must preserve the historical status of completed/cancelled/no-show appointments.

---

# 41. Doctor Workflow

Typical doctor workflow:

```text
Login
 ↓
Dashboard
 ↓
Today's Appointments
 ↓
Patient
 ↓
Medical Record
 ↓
Consultation
 ↓
Save
 ↓
Medical History Updated
```

The workflow should minimize unnecessary navigation.

---

# 42. Secretary Workflow

Typical secretary workflow:

```text
Login
 ↓
Calendar
 ↓
Select Patient
 ↓
Select Doctor
 ↓
Select Available Time
 ↓
Create Appointment
 ↓
Confirm Booking
```

The secretary should not need to navigate through clinical modules to complete ordinary booking tasks.

---

# 43. Owner Workflow

Typical owner workflow:

```text
Login
 ↓
Dashboard
 ├── Clinic Activity
 ├── Doctor Activity
 └── Appointment Activity

Management
 ├── Team
 ├── Doctors
 ├── Patients
 └── Schedules

Administration
 └── Settings
```

---

# 44. Data Visibility in UI

The frontend must never assume that a user's role alone determines every visible record.

For example:

A doctor may be able to access only a subset of patients within the clinic.

Therefore:

* Patient lists must be filtered by authorization.
* Search results must be authorization-aware.
* Filters must not reveal unauthorized values.
* Pagination totals must not expose unauthorized record counts.
* Dashboard statistics must use authorized datasets.
* Calendar data must respect doctor access.
* Medical records must be authorization-protected.
* Documents must be authorization-protected.

---

# 45. URL / Route Protection

Frontend routes should be protected according to authentication and role.

However, route protection is only a UX/security layer.

The backend must independently enforce authorization for every protected operation.

Example:

```text
/login
/forgot-password
/reset-password
/accept-invitation

/dashboard
/calendar
/patients
/patients/:patientId
/patients/:patientId/medical-record
/patients/:patientId/consultations/new
/patients/:patientId/consultations/:consultationId
/patients/:patientId/consultations/:consultationId/edit
/team
/team/new
/team/:memberId
/schedules
/activity
/settings
/account
```

This is the canonical route structure. Doctor profiles are team member pages (`/team/:memberId`).

---

# 46. Consistency Rules

Across the entire application:

* Add buttons should use consistent placement.
* Save/Cancel actions should use consistent ordering.
* Tables should behave consistently.
* Search should look and behave consistently.
* Filters should use consistent controls.
* Pagination should use a consistent component.
* Status indicators should use consistent terminology.
* Modals should behave consistently.
* Form validation should behave consistently.
* Toast/feedback behavior should be consistent.
* Date and time formatting should be consistent.
* Empty states should follow a common pattern.

---

# 47. Design System Direction

Before implementing individual pages, IATROS should establish reusable design tokens/components for:

* Typography
* Spacing
* Border radius
* Shadows
* Inputs
* Buttons
* Cards
* Tables
* Badges
* Tabs
* Modals
* Dropdowns
* Date/time pickers
* Alerts
* Toasts
* Pagination
* Loading states
* Empty states

The visual language, tokens, and component specifications are defined in `design-system.md`. Components are built in-house with CSS Modules and design tokens on top of Radix UI accessible primitives (`decisions.md` D-040).

The application should avoid having every page implement its own version of the same UI component.

---

# 48. UX Quality Gate

A screen should not be considered complete simply because its main functionality works.

Before a screen is considered complete, it should have:

* Correct role access
* Search where relevant
* Filters where relevant
* Sorting where relevant
* Pagination for multi-record tables
* Loading state
* Empty state
* Error state
* Success feedback
* Validation
* Appropriate confirmation
* Responsive behavior
* Light-mode support
* Dark-mode support
* English text
* French translation
* Accessible controls

---

# 49. V1 UI/UX Boundary

The following should not be added to the V1 interface unless explicitly approved:

* Patient portal
* Patient login
* Online patient booking
* Mobile application UI
* Google Calendar integration UI
* Outlook integration UI
* Online payments
* Subscription billing
* Advanced specialty modules
* AI clinical assistant
* Advanced laboratory workflows
* Advanced imaging workflows

The UI should be designed so these features can be added later without requiring a complete redesign.

---

# 50. Final UX Principle

The goal of IATROS is not to expose every available function.

The goal is to make the clinic's common workflows simple:

**Manage the clinic → manage the team → manage patients → schedule appointments → conduct consultations → maintain medical records → understand clinic activity.**

Every screen should support that workflow clearly and efficiently.
