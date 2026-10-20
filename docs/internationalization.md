# IATROS — Internationalization Specification

## 1. Purpose

This document defines how internationalization (i18n) and localization (l10n) are implemented in IATROS.

IATROS V1 supports:

* English
* French

Internationalization must be designed from the beginning so that adding additional languages later does not require rewriting application logic or database structures.

The language system applies consistently to:

* Authentication
* Navigation
* Dashboards
* Patients
* Medical records
* Consultations
* Appointments
* Calendar
* Doctor management
* Staff management
* Settings
* Notifications
* Validation messages
* Errors
* Empty states
* Confirmation dialogs
* Tables
* Forms
* Buttons
* Tooltips
* System-generated messages

---

# 2. Language Principles

## 2.1 No hardcoded user-facing strings

User-facing text must not be hardcoded directly inside React components.

Bad:

```text
<button>Save Patient</button>
```

Preferred:

```text
<button>{t("patients.actions.save")}</button>
```

All user-facing strings must come from translation resources.

---

## 2.2 Supported languages

V1 languages:

| Language | Code | Status |
| -------- | ---- | ------ |
| English  | `en` | V1     |
| French   | `fr` | V1     |

The architecture must allow additional languages to be added later.

---

# 3. Default Language

The application must determine the initial language using the following priority:

1. Explicitly saved user preference
2. Browser/system language if supported
3. English fallback

Unsupported browser languages must fall back to English.

Example:

```text
Browser language: fr
User preference: none
→ French
```

```text
Browser language: de
User preference: none
→ English
```

Once the user explicitly selects a language, that preference should be persisted.

---

# 4. Language Selector

A language selector must be available in an appropriate global location, primarily:

* User/profile menu
* Settings

The selector must clearly show:

* English
* Français

Avoid relying exclusively on flags because languages are not inherently represented by countries.

Example:

```text
Language

English
Français
```

Changing the language should:

1. Update the interface immediately.
2. Persist the preference.
3. Keep the user on the same page where possible.
4. Preserve current form data where technically safe.
5. Update translated validation and feedback messages.

---

# 5. Translation Structure

Translation keys must be organized by functional domain rather than by individual component.

Recommended structure:

```text
locales/
├── en/
│   └── common.json
└── fr/
    └── common.json
```

As the application grows, translations may be separated:

```text
locales/
├── en/
│   ├── common.json
│   ├── auth.json
│   ├── dashboard.json
│   ├── patients.json
│   ├── medical-records.json
│   ├── appointments.json
│   ├── doctors.json
│   ├── settings.json
│   └── errors.json
└── fr/
    ├── common.json
    ├── auth.json
    ├── dashboard.json
    ├── patients.json
    ├── medical-records.json
    ├── appointments.json
    ├── doctors.json
    ├── settings.json
    └── errors.json
```

The exact file split may evolve, but translation keys must remain predictable and domain-oriented.

**Implementation (`decisions.md` D-040):** i18next + react-i18next, with resources in `frontend/src/i18n/locales/{en,fr}/{namespace}.json`. `common` (shell, navigation, shared actions and states, statuses), `auth`, and `errors` (API error codes and `field.*` validation codes) are the core namespaces: the English ones ship with the app, and the French ones are fetched when French is first used, before anything renders in French. Feature namespaces (`account`, `team`, `clinic`, `specialties`, `doctors`, `schedules`, `patients`, `medicalRecords`, `calendar`, `dashboard`, `activity`) are loaded in both languages by the route that needs them, so switching language on a feature page never waits on the network. Consultation date-times are entered and shown in the clinic's time zone. Specialty names are translated from their stable codes (`specialties:CARDIOLOGY`). Weekday names, schedule dates, and times are formatted with `Intl` in the interface language (`useDateTimeFormat`); schedule dates and `HH:mm` times are already clinic-local, so they are formatted without any time-zone conversion. A test checks that every namespace has the same keys and interpolation values in English and French. TypeScript type-checks translation keys against the English resources so missing or misspelled keys fail the build.

---

# 6. Translation Key Convention

Keys should use a hierarchical naming convention.

Example:

```text
patients.title
patients.actions.add
patients.actions.edit
patients.actions.archive
patients.fields.firstName
patients.fields.lastName
patients.fields.dateOfBirth
patients.fields.primaryDoctor
patients.messages.created
patients.messages.updated
```

Avoid keys based on the current English sentence:

```text
"Add a new patient to the clinic"
```

Prefer semantic keys:

```text
patients.actions.add
```

This allows the English wording to change without changing application logic.

---

# 7. Shared Translations

Common interface terms should be centralized.

Examples:

```text
common.actions.save
common.actions.cancel
common.actions.edit
common.actions.delete
common.actions.close
common.actions.confirm
common.actions.search
common.actions.filter
common.actions.reset
common.actions.back
common.actions.next
common.actions.previous
common.actions.retry
common.actions.create
common.actions.update
common.actions.archive
common.actions.activate
```

Common states:

```text
common.loading
common.noResults
common.noData
common.error
common.success
common.required
common.optional
```

---

# 8. Medical Terminology

Medical terminology must be translated carefully.

The French interface should use appropriate professional French terminology rather than literal word-for-word translations.

Examples:

| English           | French               |
| ----------------- | -------------------- |
| Patient           | Patient              |
| Medical Record    | Dossier médical      |
| Consultation      | Consultation         |
| Diagnosis         | Diagnostic           |
| Allergy           | Allergie             |
| Medication        | Médicament           |
| Chronic Condition | Maladie chronique    |
| Medical History   | Antécédents médicaux |
| Follow-up         | Suivi                |
| Doctor            | Médecin              |
| Appointment       | Rendez-vous          |
| Specialty         | Spécialité           |

Medical terminology should remain consistent throughout the application.

---

# 9. User-Generated Content

User-entered data must generally **not be translated**.

Examples:

* Patient names
* Doctor names
* Clinic names
* Medical notes
* Consultation notes
* Patient addresses
* Custom specialty text
* Appointment reasons entered by users
* Uploaded document names

For example, if a doctor enters:

```text
Patient reports persistent back pain.
```

Changing the application language to French must not automatically translate the note.

The interface language and user-generated medical content are separate concerns.

---

# 10. Database Language Independence

Database records must not depend on translated UI strings.

Do not store:

```text
status = "Completed"
```

or:

```text
status = "Terminé"
```

Instead, store stable internal values such as:

```text
COMPLETED
```

The frontend translates the value:

```text
COMPLETED → Completed
COMPLETED → Terminé
```

This applies to:

* Appointment statuses
* Roles
* Permission identifiers
* System states
* Enumerations
* Internal categories
* Workflow states

This prevents changing the interface language from corrupting or changing stored data.

---

# 11. Roles and Permissions

Roles must use stable internal identifiers.

Example:

```text
OWNER
DOCTOR
SECRETARY
```

The interface translates them:

| Internal value | English   | French         |
| -------------- | --------- | -------------- |
| OWNER          | Owner     | Administrateur |
| DOCTOR         | Doctor    | Médecin        |
| SECRETARY      | Secretary | Secrétaire     |

Authorization logic must always use the internal role value, never translated text.

---

# 12. Appointment Statuses

Appointment statuses must use stable internal values.

Recommended:

```text
SCHEDULED
CONFIRMED
WAITING
IN_CONSULTATION
COMPLETED
CANCELLED
NO_SHOW
```

Translations:

| Internal value  | English         | French          |
| --------------- | --------------- | --------------- |
| SCHEDULED       | Scheduled       | Planifié        |
| CONFIRMED       | Confirmed       | Confirmé        |
| WAITING         | Waiting         | En attente      |
| IN_CONSULTATION | In consultation | En consultation |
| COMPLETED       | Completed       | Terminé         |
| CANCELLED       | Cancelled       | Annulé          |
| NO_SHOW         | No-show         | Absent          |

The exact French wording should remain consistent across calendar, appointment details, dashboards, and filters.

---

# 13. Dates and Times

Date and time formatting must respect the selected locale.

The application must not manually construct localized date strings.

Examples:

English:

```text
September 25, 2026
```

French:

```text
25 septembre 2026
```

Time formatting must also respect the locale and application conventions.

The backend should store date/time values in a consistent machine-readable format.

Localization happens at the presentation layer.

---

# 14. Time Zones

Appointments must be stored and processed using an explicit time-zone strategy.

The application must never rely on the server's local time implicitly.

The clinic's configured time zone should be the primary reference for clinic scheduling.

Displaying appointment times must take the relevant clinic/user context into account.

Time-zone handling must be consistent across:

* Calendar
* Appointments
* Doctor schedules
* Availability
* Dashboards
* Notifications
* API responses

---

# 15. Numbers and Formatting

Numbers should be formatted according to locale where appropriate.

Examples include:

* KPI values
* Counts
* Percentages
* Dates
* Times

The application should use standard internationalization APIs rather than manually formatting numbers.

**Implementation:** `useNumberFormat` wraps `Intl.NumberFormat` in the interface language for counts, percentages, and signed comparisons (`+12 %`, `−3 pts`); KPI tiles and the activity page use it exclusively.

---

# 16. Currency

Billing is outside V1.

However, if monetary values are introduced later, currency formatting must be localized without mixing currency formatting with translation logic.

Currency must be represented internally using stable currency codes such as:

```text
EUR
USD
```

rather than localized strings.

---

# 17. Pluralization

Translations must support proper pluralization.

Example:

English:

```text
1 patient
5 patients
```

French:

```text
1 patient
5 patients
```

The application must not construct pluralized strings manually.

Avoid:

```text
count + " patient(s)"
```

Use translation-aware pluralization instead.

---

# 18. Dynamic Values

Dynamic values must be inserted through translation parameters.

Example:

```text
patients.messages.totalPatients
```

with:

```text
12 patients
```

French:

```text
12 patients
```

Dynamic values must never be concatenated into translated sentences in a way that prevents natural grammar.

---

# 19. Validation Messages

Validation messages must be translated.

Examples:

```text
First name is required.
```

```text
Le prénom est obligatoire.
```

Validation should use stable error identifiers wherever possible.

The frontend maps those identifiers to the currently selected language.

Backend validation errors should also provide machine-readable error codes so the frontend can display the correct localized message.

---

# 20. API Error Messages

API responses should not require the backend to return fully translated UI sentences.

Prefer:

```text
code: PATIENT_NOT_FOUND
```

rather than making frontend logic depend on:

```text
"Patient not found"
```

The frontend can translate the error code according to the selected language.

Codes without a dedicated message fall back by status: `404` → "not available to you", `409` → "someone else changed this item", `5xx` → "something went wrong on our side", anything else → a generic retry message. Feature-specific codes (e.g. document upload errors) may be translated in the feature's namespace instead of `errors`, which keeps the sign-in bundle small.

For security-sensitive errors, the backend must avoid exposing unnecessary internal information.

---

# 21. Notifications and Feedback

Success, warning, error, and informational messages must be translated.

Examples:

```text
Patient created successfully.
```

```text
Patient créé avec succès.
```

The same message should use the same translation key regardless of where it appears.

---

# 22. Search and Filtering

Search must remain independent from interface language.

UI labels are translated:

```text
Search patients
Rechercher des patients
```

But user-generated data remains unchanged.

For system-defined categories and statuses, filtering must operate on stable internal values.

Example:

```text
COMPLETED
```

rather than:

```text
Completed
```

or:

```text
Terminé
```

---

# 23. Sorting

Sorting must be locale-aware when sorting visible text.

Names and other localized/user-facing strings should use appropriate locale-aware comparison where required.

Database sorting and UI sorting must remain consistent with the type of data being sorted.

---

# 24. Right-to-Left Considerations

Arabic is not a V1 language.

However, the frontend should avoid architecture that makes future RTL support unnecessarily difficult.

Components should avoid hardcoded left/right positioning when logical CSS properties can be used.

Prefer logical properties such as:

```text
margin-inline
padding-inline
inset-inline
border-inline
```

where appropriate.

This keeps the UI more extensible for future languages.

---

# 25. Forms

Forms must support translation without changing layout unexpectedly.

Labels, placeholders, hints, validation messages, and buttons must all be localized.

Placeholder text must not replace labels.

Longer French translations must be considered during UI design so that:

* buttons do not overflow
* navigation remains usable
* tables remain readable
* cards do not break
* dialogs remain usable

---

# 26. Calendar Localization

Calendar interfaces must respect the selected language for:

* Month names
* Day names
* Date formatting
* Week labels
* Appointment status labels
* Calendar controls

The underlying appointment data remains language-independent.

The calendar must continue to follow the clinic's scheduling rules regardless of interface language.

---

# 27. Navigation

Navigation labels must be translated consistently.

Example:

English:

```text
Overview / My day
Calendar
Patients
Team
Schedules / My schedule
Activity / My activity
Settings
```

French:

```text
Vue d'ensemble / Ma journée
Calendrier
Patients
Équipe
Plannings / Mon planning
Activité / Mon activité
Paramètres
```

The canonical navigation per role is defined in `ui-ux.md` §38.

Navigation permissions remain role-based and independent of language.

---

# 28. Accessibility

Translated interfaces must preserve accessibility.

This includes:

* translated accessible labels
* translated button descriptions
* translated validation errors
* translated form instructions
* translated status announcements
* correct language metadata on the document

Screen readers must be able to determine the current interface language.

---

# 29. Language Metadata

The application must expose the currently selected language through the frontend i18n system and document metadata.

The HTML document language should reflect the current locale.

Example:

```text
lang="en"
```

or:

```text
lang="fr"
```

---

# 30. Translation Completeness

A translation must never silently fall back to displaying an internal translation key such as:

```text
patients.actions.add
```

in the production interface.

During development, missing translations should be detectable.

A translation quality check should identify:

* Missing keys
* Extra keys
* Invalid interpolation parameters
* Untranslated UI strings
* Inconsistent terminology

---

# 31. English as Development Reference

English should serve as the primary reference language for the initial translation structure.

Every new user-facing feature should first define its English translation keys and then provide the corresponding French translations.

A feature is not considered complete if only one language has been implemented.

---

# 32. Translation Review

French translations should be reviewed for:

* grammatical correctness
* natural wording
* medical terminology
* consistency
* appropriate professional tone
* UI length
* contextual meaning

Literal machine translation should not automatically be considered final.

French typography (enforced by `src/i18n/resources.test.ts`):

* the typographic apostrophe `’`, never `'`;
* a narrow no-break space (U+202F) before `?`, `!`, and `;`, and a no-break space (U+00A0) before `:` and inside `« »`, so punctuation never wraps onto its own line;
* "cabinet" for the practice (not "clinique"), "responsable" for the owner role, "médecin traitant" for the primary doctor.

---

# 33. Language Persistence

The selected language must persist across sessions.

The preference should be associated with the authenticated user when appropriate.

Before authentication, the application may persist the language locally.

After authentication, the user's saved preference should become the authoritative preference.

**Implementation (`decisions.md` D-043):** the browser stores the language only when the user explicitly chooses one (sign-in screen, user menu, account page). A signed-in choice is also saved with `PUT /api/v1/auth/me/preferences`. On sign-in the account preference is applied; if the account has none and the device has no explicit choice, the clinic default language is used. Otherwise the browser language or English applies (§3).

---

# 34. Authentication and Language

Authentication screens must also be localized.

This includes:

* Login
* Forgot password
* Reset password
* Password validation
* Authentication errors
* Account status messages

A user should be able to select their language before logging in.

---

# 35. Emails and Future Notifications

Email and notification localization is outside the initial core UI implementation but must be architecturally supported.

Future system-generated emails should use the recipient's preferred language.

Examples:

* Password reset
* Appointment reminders
* Appointment confirmations
* Account invitations
* System notifications

The email language should not depend on the language of the administrator who triggered the action.

---

# 36. Backend Internationalization

The backend should not contain UI-specific translation logic unless required for server-generated content.

Backend responsibilities include:

* stable error codes
* validation identifiers
* stable enum values
* locale-aware processing where technically necessary

Frontend responsibilities include:

* UI translations
* labels
* buttons
* navigation
* localized messages
* presentation formatting

---

# 37. Testing

Internationalization must be included in testing.

Tests should verify:

* English renders correctly.
* French renders correctly.
* No required translation keys are missing.
* Language switching works.
* Language preference persists.
* Validation messages change language.
* API error codes map correctly to translations.
* Calendar localization works.
* Dates and numbers format correctly.
* Long French labels do not break the interface.
* Role/permission logic does not depend on translated text.
* Database values remain language-independent.

---

# 38. Development Rules

Developers must follow these rules:

1. Never hardcode user-facing text in components.
2. Never use translated labels as business-logic identifiers.
3. Never store translated enum values in the database.
4. Never translate user-generated medical content automatically.
5. Always provide English and French translations for new V1 UI.
6. Use translation keys rather than sentence-based keys.
7. Use locale-aware date/time/number formatting.
8. Keep backend business logic independent of UI language.
9. Test both English and French for every significant UI feature.
10. Keep terminology consistent across the application.

---

# 39. V1 Scope

### Included

* English
* French
* Language selector
* Persistent language preference
* Localized navigation
* Localized forms
* Localized validation
* Localized notifications
* Localized errors
* Localized dashboards
* Localized calendar
* Localized medical-record interface
* Locale-aware dates and times
* Locale-aware numbers
* Translation completeness checks

### Outside V1

* Arabic
* Additional languages
* RTL interface
* Multilingual patient-generated content
* Automatic medical-content translation
* Fully localized email notification system
* Advanced regional localization
* Country-specific regulatory localization

---

# 40. Definition of Done

Internationalization is complete when:

* English and French are available throughout V1.
* No user-facing UI text is hardcoded.
* Language switching works without unnecessary navigation.
* User language preference persists.
* Dates/times/numbers respect the selected locale.
* Medical terminology is consistent.
* Backend logic uses language-independent identifiers.
* Validation and API errors can be localized.
* Calendar labels are localized.
* Missing translations can be detected during development.
* Both languages have been tested across the major workflows.
* Adding a third language would not require rewriting business logic.

---

# 41. Final Principle

**IATROS must separate application language from application data.**

The interface can change from English to French without changing the underlying clinic, patient, medical, appointment, permission, or workflow data.

Internationalization is therefore a presentation-layer concern supported by stable backend identifiers and a language-independent domain model.
