# IATROS — Design System & Visual Identity

## 1. Purpose

This document defines how IATROS looks and feels.

`ui-ux.md` defines **what** each screen contains and how workflows behave. This document defines the **visual language, layout model, components, and interaction patterns** that every screen must use.

The goal: IATROS must feel like a premium, calm, trustworthy medical product with its own recognizable identity, not a generic admin template.

---

# 2. Design Principles

1. **Calm over busy.** A clinic is a stressful environment. The interface lowers cognitive load: fewer borders, fewer colors, more whitespace, one clear primary action per area.
2. **Time is the backbone.** Clinic work is organized by time. "Now", "today", and "next" are always easy to find.
3. **The patient is always unmistakable.** On every clinical screen, the user always knows which patient they are looking at.
4. **Distinctive through structure, not decoration.** Identity comes from layout, typography, rhythm, and interaction quality. No decorative gradients, illustrations-for-the-sake-of-it, glassmorphism, or heavy shadows.
5. **Every element earns its place.** If an element does not help the user decide or act, remove it.
6. **Quiet until it matters.** Color and emphasis are reserved for status, risk, and primary actions. Warnings (allergies, conflicts) stand out because everything else is restrained.
7. **Same pattern, same place.** A user who learns one screen has learned them all.

## 2.1 Direction

These rules govern every screen and module. They sit on top of the principles above.

1. **Minimal but not empty.** Leave room to read, and still give every screen a clear structure: a title, the thing the user is working on, and one primary action.
2. **Premium but not flashy.** Warm white, navy, sage, and indigo. No extra hues, no decorative gradients, no effects that do not help the task.
3. **Functional before decorative.** Color carries meaning (action, status, risk) or it stays neutral.
4. **Consistent across all modules.** Calendar, patients, records, team, schedules, activity, and settings use the same tokens, type, spacing, and components.
5. **Responsive on desktop, tablet, and mobile.** The rail, context bar, and page content reflow at the breakpoints in §4. Nothing is designed for one width only.
6. **Light and dark modes.** Both themes ship together. A component that only works in one theme is unfinished.

---

# 3. Identity

## 3.1 Name and concept

*Iatros* (ἰατρός) is Greek for "physician". The visual concept is **"clinical clarity"**: the precision of a well-kept medical chart, combined with the warmth of a good practice.

## 3.2 Signature elements

These elements make IATROS recognizable and must be used consistently:

1. **The Now line.** A thin sage line marks the current time in every time-based view (calendar, doctor's day timeline, today's queue). It is IATROS's visual signature.
2. **The Patient band.** On every patient and clinical screen, a slim, sticky band at the top shows the patient's identity: full name, age and date of birth, primary doctor, and an allergy indicator. It never scrolls away.
3. **Ink-on-paper surfaces.** Light mode uses warm white paper. Dark mode uses navy ink, never pure black. Surfaces are separated by tone and hairlines, not shadows.
4. **Status as a quiet dot.** Status is shown as a small colored dot plus a text label, never as large saturated badges.
5. **Tabular time.** All times, durations, and numbers use tabular figures so columns of times align perfectly.

## 3.3 Logo

The mark is a medical cross cut by a two-blade leaf. It is the only gradient in the product (`--logo-from`, `--logo-mid`, `--logo-to`). Interface chrome stays flat.

Use the variant that matches the surface:

* **Color mark** on light paper and surfaces (navigation rail, sign-in form, not-found).
* **White mark** on dark surfaces and on the sign-in photograph, with the wordmark in `--color-text-on-media`.
* **Horizontal lockup** — mark, the word "IATROS" in the UI typeface with wide tracking, and the translated brand tagline — on the sign-in photograph. The rail shows the mark and wordmark only; the clinic name sits under the wordmark.
* **App icon** — the color mark on a light rounded tile (`--logo-tile`) — for the favicon and the initial loading screen. In a dark browser chrome the favicon switches to the white mark.

---

# 4. Layout Model

IATROS uses a **workspace layout**, not the classic "sidebar + cards dashboard".

```text
┌────┬──────────────────────────────────────────────┬──────────────┐
│    │  Context bar: page title · scope · actions    │              │
│ R  ├──────────────────────────────────────────────┤   Context    │
│ a  │                                              │    panel     │
│ i  │              Main workspace                  │  (optional,  │
│ l  │                                              │  slides in)  │
│    │                                              │              │
└────┴──────────────────────────────────────────────┴──────────────┘
```

## 4.1 Rail (primary navigation)

* A slim navigation rail with icon **and** text label for each item (labels are never icon-only).
* Clinic name and the IATROS mark at the top (color on light, white on dark); user menu at the bottom.
* Shows only the items allowed for the user's role (`ui-ux.md` §38).
* Below 1024px it collapses to icons with accessible labels and tooltips; below 768px it becomes a bottom navigation bar.

## 4.2 Context bar

Replaces the generic top header. It contains, from start to end:

* page title (and breadcrumb only when nested more than one level);
* the page's **scope controls** (date navigation, doctor selector, view switch);
* the page's **single primary action** (e.g. "Book appointment", "Add patient").

Language and theme controls live in the user menu and settings, not in the context bar.

## 4.3 Main workspace

* Content width is fluid with a maximum readable width for text-heavy pages (forms, records: ~880px). Data-heavy pages (calendar, tables) use the full width.
* Pages are built from **sections** separated by whitespace and section titles, not by stacking bordered cards.
* Cards are used only for items that are genuinely independent objects (e.g. a KPI tile, an appointment block).

## 4.4 Context panel

A right-side panel that slides in over or beside the workspace for **viewing and quick actions without losing place**:

* appointment details from the calendar;
* patient quick view from a list;
* quick status changes.

Rules:

* The panel never hosts long or complex workflows (those get a full page).
* Closing the panel returns focus to the element that opened it.
* The panel state is reflected in the URL where it represents a resource (e.g. `?appointment=<id>`), so it survives refresh and can be shared internally. IDs only; no sensitive data in URLs.

## 4.5 Choosing the container

| Need                                               | Use                       |
| -------------------------------------------------- | ------------------------- |
| View details while keeping list/calendar visible   | Context panel             |
| Short focused form (≤ ~6 fields), confirmation     | Dialog                    |
| Long form, clinical entry, multi-section editing   | Full page                 |
| Quick choice (status, filter)                      | Popover / menu            |

## 4.6 Authentication

Sign-in and password screens are not the workspace. They use a two-column layout: a photographic brand panel, and a form column on `--color-surface`.

* The photograph is decorative. The white lockup (mark, wordmark, and brand tagline) and a short line sit at the bottom, on a scrim of `--color-media`, in `--color-text-on-media`.
* The form column holds the color mark, the screen title, the fields, and one primary action. Language can be switched before signing in. The mark is white when that column is dark.
* Below 768px the photograph becomes a short banner and the form uses the rest of the screen.

---

# 5. Role Home Screens

Each role lands on the screen that matches its real workflow:

* **Doctor → "My day".** A vertical timeline of today's appointments with the Now line, the current/next patient emphasized, one-click "Start consultation", and a short list of patients needing follow-up.
* **Secretary → Calendar.** Opens on today's calendar for all doctors, with a fast "Book" flow: find patient → choose slot → confirm.
* **Owner → Overview.** Today's clinic pulse (appointments by status, waiting room, doctors' load), followed by period KPIs. Charts only when a trend matters.

---

# 6. Color

## 6.1 Palette

The interface uses four colors, in every module:

* **Warm white** — paper. The canvas, working surfaces, and the app-icon tile.
* **Navy** — ink. Primary text, the wordmark, and every dark-mode surface. Dark mode is navy, never pure black.
* **Sage** — the only functional accent. Primary actions, focus, selection, the Now line, and confirmed appointments.
* **Indigo** — information, and the in-consultation state. It is not a second button color.

Neutrals still carry about 90% of the screen. Warning, danger, and success stay outside this set so risk and completion are not mistaken for the brand. The logo gradient (`--logo-from`, `--logo-mid`, `--logo-to`) belongs to the mark only. Do not reuse it on controls, surfaces, or text.

## 6.2 Tokens

Components must use **semantic tokens only**, never raw color values.

```text
--color-canvas          page background ("paper" / "ink")
--color-surface         working surfaces (tables, panels, forms)
--color-surface-raised  overlays (dialogs, popovers, panels)
--color-surface-sunken  inset areas (inputs, code, wells)
--color-border          hairlines
--color-border-strong   dividers that must be seen, chips, secondary buttons, hover outlines
--color-border-control  outlines of inputs and selects (≥ 3:1 against every surface)
--color-text            primary text
--color-text-muted      secondary text
--color-text-subtle     placeholders, disabled
--color-accent          primary actions, focus, Now line
--color-accent-hover
--color-accent-contrast text/icons on accent
--color-accent-soft     selected rows, active nav item background
--color-focus-ring
--color-success / -soft
--color-warning / -soft
--color-danger  / -soft
--color-danger-contrast text/icons on danger buttons
--color-info    / -soft
--color-scrim           backdrop behind dialogs and overlays
--color-media           photographic sign-in panel and its scrim (same in both themes)
--color-text-on-media   text on that panel (same in both themes)
--color-status-{scheduled|confirmed|waiting|in-consultation|completed|cancelled|no-show}
```

Layout and stacking tokens (also in `tokens.css`):

```text
--rail-width / --rail-width-collapsed   navigation rail, full and icon-only
--bottom-nav-height                     small-screen bottom navigation
--context-panel-width / --dialog-width
--icon-sm / --icon-md                   icon sizes
--z-sticky < --z-nav < --z-overlay < --z-toast
```

## 6.3 Reference values

| Token                 | Light     | Dark      |
| --------------------- | --------- | --------- |
| canvas                | `#F4F0E6` warm white | `#0E1824` navy |
| surface               | `#FBF8F2` | `#152232` |
| surface-raised        | `#FFFCF7` | `#1C2B3D` |
| surface-sunken        | `#E9E4D8` | `#0A121C` |
| border                | `#E3DCD0` | `#2C3C50` |
| border-strong         | `#CDC4B4` | `#3D5168` |
| border-control        | `#7A7268` | `#7E94AA` |
| text                  | `#13283C` navy | `#F6F3EC` warm white |
| text-muted            | `#3E5164` | `#C9D3DC` |
| text-subtle           | `#4A5D70` | `#A8B6C2` |
| accent                | `#1F5646` sage | `#A9CDB8` sage |
| accent-hover          | `#184838` | `#C5DDD2` |
| accent-contrast       | `#FFFFFF` | `#0E1824` navy |
| accent-soft           | `#E3EFE8` | `#16352C` |
| info                  | `#2A3A86` indigo | `#C4CBF2` indigo |
| media                 | `#0E1824` navy | `#0E1824` navy |
| text-on-media         | `#F6F3EC` | `#F6F3EC` |

Exact values may be tuned during implementation, but every text/background pair must meet **WCAG 2.2 AA** (4.5:1 for body text, 3:1 for large text and UI boundaries) in both themes. `src/styles/tokens.test.ts` enforces this: every text and semantic color on every surface (4.5:1), text on filled and tinted backgrounds (4.5:1), and control outlines, the focus ring, and status colors (3:1). Placeholder (`text-subtle`) text is held to 4.5:1 too.

## 6.4 Appointment status colors

| Status          | Hue        | Meaning                     |
| --------------- | ---------- | --------------------------- |
| SCHEDULED       | Navy slate | Planned, not yet confirmed  |
| CONFIRMED       | Sage       | Confirmed by patient        |
| WAITING         | Amber      | Patient has arrived         |
| IN_CONSULTATION | Indigo     | With the doctor now         |
| COMPLETED       | Green      | Done. Distinct from sage, so it is not read as confirmed. |
| CANCELLED       | Warm gray (struck/faded) | No longer happening |
| NO_SHOW         | Red        | Patient did not come        |

Status is **always** communicated by text label (and dot) in addition to color.

---

# 7. Typography

* **Typeface:** Geist (variable, self-hosted), with `system-ui` fallback. Excellent legibility, full French diacritics support, and tabular figures.
* **Monospace:** Geist Mono, only for identifiers where needed.
* Numbers in tables, times, durations, and KPIs use `font-variant-numeric: tabular-nums`.

## 7.1 Type scale (rem, base 16px)

| Token        | Size / line-height | Weight | Use                          |
| ------------ | ------------------ | ------ | ---------------------------- |
| display      | 2.25 / 1.15        | 600    | Role home greeting, big KPI  |
| title-1      | 1.5 / 1.25         | 600    | Page titles                  |
| title-2      | 1.125 / 1.35       | 600    | Section titles               |
| body         | 0.9375 / 1.5       | 400    | Default text (15px)          |
| body-strong  | 0.9375 / 1.5       | 550    | Emphasis, table primary cell |
| small        | 0.8125 / 1.45      | 400    | Secondary info, metadata     |
| label        | 0.75 / 1.3         | 550    | Field labels, column headers (sentence case, slight letter-spacing) |

Rules:

* Sentence case everywhere ("Book appointment", not "Book Appointment"). This also reads naturally in French.
* No all-caps body text.
* Maximum line length for prose: ~72 characters.

---

# 8. Spacing, Shape, Elevation

## 8.1 Spacing

4px base scale: `4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

* Within a component: 4–12.
* Between related elements: 12–16.
* Between sections: 32–48.

## 8.2 Radius

* `--radius-sm` 6px — inputs, small buttons, badges
* `--radius-md` 10px — buttons, menus, appointment blocks
* `--radius-lg` 14px — panels, dialogs

## 8.3 Elevation

* Resting surfaces: no shadow; separated by tone and 1px hairline.
* Overlays only (dialogs, popovers, context panel, toasts): one soft shadow level.
* Dark mode: elevation shown by lighter surface tone, not by shadow.

## 8.4 Density

* Default row height in tables: 48px; compact option: 40px (calendar-heavy users may prefer it).
* Minimum interactive target: 40×40px desktop, 44×44px touch.

---

# 9. Motion

* Motion explains change; it never decorates.
* Durations: 120ms (hover/press), 180ms (popovers, menus), 240ms (panels, dialogs).
* Easing: standard ease-out for entering, ease-in for leaving.
* No looping animations except loading indicators.
* `prefers-reduced-motion: reduce` disables transforms and non-essential transitions.

---

# 10. Iconography

* Lucide icons, 1.5px stroke, 16px (inline) and 20px (navigation).
* Icons support text; they never replace labels for actions or navigation.
* Icon-only buttons (e.g. close) require an accessible label and tooltip.

---

# 11. Core Components

Every component must support light and dark themes, keyboard use, visible focus, and EN/FR text lengths.

## 11.1 Buttons

Variants: `primary` (accent, max one per area), `secondary` (neutral surface with border), `ghost` (text only), `danger` (for destructive actions, used inside confirmations).

States: default, hover, active, focus-visible, disabled, loading (spinner + label preserved; width does not jump).

## 11.2 Inputs & forms

* Label above the field, always visible; placeholder is never a label.
* Optional fields are marked "(optional)"; required is the default.
* Helper text below the field; error text replaces helper text and is linked via `aria-describedby`.
* Validation on blur and on submit; errors never appear while the user is still typing a first value.
* On submit failure, focus moves to the first invalid field and a summary appears at the top for long forms.
* Forms with unsaved changes warn before navigation.

## 11.3 Data tables

* Calm by default: no zebra stripes, no vertical borders, hairline row separators.
* Sticky header; sortable columns show the sort direction icon only on the sorted column (and on hover).
* Primary column (e.g. patient name) is bold and is the row's link target.
* Row actions in a trailing overflow menu; the most frequent action may be inline.
* Toolbar above the table: search (start), filters (as removable chips), result count, reset.
* Pagination below: "21–40 of 137", previous/next, page size.
* On narrow screens, rows become stacked list items rather than horizontally squeezed tables where possible.

## 11.4 Filters

* Filters appear as chips: "Doctor: Dr. Martin ✕".
* "Clear all" is visible whenever any filter is active.
* Filters and search are reflected in the URL so they survive refresh and back navigation.

## 11.5 Status indicator

Dot + label, colored by status token. Used identically in calendar, tables, panels, dashboards.

## 11.6 Patient band

Sticky band: avatar initials, full name, age · date of birth, primary doctor, allergy indicator. Allergy indicator is the only element in the band that may use the danger color, and it is always labeled ("2 allergies"), never color-only.

## 11.7 Calendar

* Day / week / month views; week view is default on desktop, day view on small screens.
* Time grid with hairlines every hour and lighter lines every 30 minutes; non-working hours shown on a sunken background so availability is instantly visible.
* Appointment blocks: patient name (strong), time range (tabular), type; status dot on the leading edge.
* The Now line spans the current day.
* Clicking a free slot inside working hours opens the booking dialog prefilled with doctor, date, and time.
* Multi-doctor view: doctors as columns in day view.

## 11.8 KPI tiles

Large tabular number, short label, and comparison to the previous period (e.g. "+12% vs last week"), with direction shown by both sign and icon. Tiles are links when a detailed view exists.

**Implementation:** `shared/ui/KpiTile` (with `KpiGrid`). The comparison's colour comes from its tone (positive, negative, neutral) rather than its direction, because a rising no-show rate is bad news; rates compare in percentage points, counts in percent, and no comparison is shown when the previous value is 0. Trends are CSS bars backed by a visually hidden data table, so no chart library is needed.

## 11.9 Feedback

| Situation                               | Pattern                              |
| --------------------------------------- | ------------------------------------ |
| Field-level validation                  | Inline under the field               |
| Action succeeded                        | Toast (bottom end, 4s, dismissible)  |
| Action failed (recoverable)             | Inline alert near the action + keep user input |
| Page could not load                     | Error state in place of content + Retry |
| Persistent condition (e.g. read-only)   | Banner at top of the workspace       |
| Destructive/consequential action        | Confirmation dialog stating the consequence |

Toasts never carry critical information that the user must act on.

## 11.10 Loading

* Skeletons that mirror the final layout for page and section loads.
* No full-screen spinners after the initial app load.
* Buttons show progress inline and are disabled while submitting.
* Data refreshes in the background keep showing existing data (no flashing to skeleton).

## 11.11 Empty states

Structure: short title, one sentence of explanation, one primary action (if the user can act).

* **Truly empty:** "No patients yet" + "Add patient".
* **No results:** "No patients match "Martin"" + "Clear search".
* **No permission to act:** explanation without an action.

No large illustrations; a single subtle icon is enough.

---

# 12. Writing Style

* Plain, calm, professional. Address the user directly ("You", "Vous").
* Short labels with verbs for actions: "Book appointment", "Save changes", "Cancel appointment".
* Never blame the user. Explain what happened and what to do next.
* Avoid technical words (no "error 409", "null", "invalid request").
* French uses professional medical vocabulary (`internationalization.md` §8) and the formal "vous".
* Design for French text being ~20–30% longer than English: buttons and labels must wrap or grow, never truncate critical text.

---

# 13. Formatting Conventions

* Dates and times use `Intl` with the active locale.
* English: `Mon, Apr 12`, 12-hour or 24-hour following the browser locale; French: `lun. 12 avr.`, always 24-hour.
* Weeks start on Monday for French and for the clinic default; configurable later.
* Durations: "30 min", "1 h 15".
* Relative time for recent activity ("5 min ago" / "il y a 5 min"), absolute date on hover/focus.

---

# 14. Accessibility Baseline

* WCAG 2.2 AA in both themes.
* Visible focus ring (2px accent + offset) on every interactive element; never removed.
* Complete keyboard paths for booking, patient search, and status changes.
* Dialogs and panels trap focus while open and restore it on close.
* Live regions announce toasts and async results.
* `lang` attribute follows the active language.
* Never rely on color, position, or icon alone to convey meaning.

---

# 15. Theming Implementation

* Tokens are CSS custom properties defined in `src/styles/tokens.css`.
* Themes are applied with `data-theme="light" | "dark"` on `<html>`. The default follows `prefers-color-scheme`; an explicit user choice is persisted.
* Components never contain hard-coded colors, font sizes, or spacing values. They use tokens only.
* The theme is applied before first paint to avoid a flash of the wrong theme.

---

# 16. Responsive Breakpoints

| Name | Min width | Behavior                                              |
| ---- | --------- | ----------------------------------------------------- |
| sm   | 0         | Bottom navigation, stacked lists, day calendar        |
| md   | 768px     | Icon rail, two-column forms where useful              |
| lg   | 1024px    | Full rail with labels, context panel overlays content |
| xl   | 1440px    | Context panel sits beside content                     |

Desktop and tablet are the primary targets; small screens must remain usable for viewing and quick actions.

---

# 17. Performance Budget (UX-facing)

* Initial JS for the login route: ≤ 150 KB gzip.
* Route-level code splitting for every feature.
* Interactions respond within 100ms; anything longer shows progress.
* Lists over a few hundred rows in a single view are paginated or virtualized.

---

# 18. Quality Gate

A screen is not visually complete until it:

* uses only design tokens and shared components;
* has loading, empty, error, and success states following §11;
* works in light and dark themes;
* works in English and French without overflow;
* passes keyboard-only use and visible-focus checks;
* respects the role-specific workflow in §5.
