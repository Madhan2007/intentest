# Requirement: User Settings

**Module:** `user-settings` (new)
**DB target:** `opzuser` (per-company)
**Access level:** Any authenticated user (manages their own settings only)
**Architecture ref:** [doc 25 §5](../architecture/25-common-features-design.md)

---

## What It Does

Allows a logged-in user to view and update their personal preferences such as language, timezone, UI theme, and notification toggles. Settings are stored per-user in the company's `opzuser` database.

- **GET** returns the current user's settings (or defaults if no row exists yet).
- **PUT** upserts the settings row — creating it on first save, updating on subsequent saves.

The `user_id` is always taken from the active session — users cannot read or write another user's settings.

---

## DB Schema

**Table:** `user_settings` — in `opzuser` (per-company)

| Column | Type | Nullable | Default | Notes |
|--------|------|---------|---------|-------|
| `user_id` | uuid | No | — | PK; FK → `id_user.id` (CASCADE on delete) |
| `preferred_language` | text | Yes | `null` | ISO 639-1 code, e.g. `en`, `ta`, `hi` |
| `timezone` | text | Yes | `null` | IANA name, e.g. `Asia/Kolkata`, `UTC` |
| `theme` | text | Yes | `null` | `lite` \| `rich` \| `system` |
| `email_notifications_enabled` | boolean | No | `true` | Toggle for email alerts |
| `updated_at` | timestamptz | No | `now()` | Set automatically on every upsert |

When `preferred_language`, `timezone`, or `theme` are `null`, the client falls back to the application default or browser locale.

---

## API Endpoints

Base path: `/api/v1/opzhub/user-settings`

### GET — Read current user's settings

**URL:** `GET /api/v1/opzhub/user-settings/profile`
**Auth:** Required.

**Response (200 OK):**
```json
{
  "ok": true,
  "data": {
    "preferred_language": "en",
    "timezone": "Asia/Kolkata",
    "theme": "lite",
    "email_notifications_enabled": true,
    "updated_at": "2026-09-11T10:00:00Z"
  },
  "correlation_id": "..."
}
```

If no settings row exists yet, returns the same structure with all nullable fields as `null` and `email_notifications_enabled: true`.

### PUT — Update current user's settings

**URL:** `PUT /api/v1/opzhub/user-settings/profile`
**Auth:** Required.

**Request:**
```json
{
  "preferred_language": "ta",
  "timezone": "Asia/Kolkata",
  "theme": "lite",
  "email_notifications_enabled": false
}
```

All fields are optional. Fields omitted from the request are left unchanged (partial update via UPSERT).

**Response (200 OK):** Same shape as the GET response, showing the updated state.

---

## SQL Commands

Two named SQL files in `modules/user-settings/db/commands/`:

**`user_settings.find_by_user_id.sql`**
```sql
SELECT user_id,
       preferred_language,
       timezone,
       theme,
       email_notifications_enabled,
       updated_at
  FROM user_settings
 WHERE user_id = :user_id;
```

**`user_settings.upsert.sql`**
```sql
INSERT INTO user_settings (
    user_id, preferred_language, timezone, theme,
    email_notifications_enabled, updated_at
) VALUES (
    :user_id, :preferred_language, :timezone, :theme,
    :email_notifications_enabled, now()
)
ON CONFLICT (user_id) DO UPDATE
   SET preferred_language          = EXCLUDED.preferred_language,
       timezone                    = EXCLUDED.timezone,
       theme                       = EXCLUDED.theme,
       email_notifications_enabled = EXCLUDED.email_notifications_enabled,
       updated_at                  = now();
```

---

## Files to Create

```
modules/user-settings/
├── module.yaml                                        ← EXISTS
├── backend/src/main/java/com/managemyopz/modules/usersettings/
│   ├── UserSettingsAutoConfiguration.java             ← NEW
│   ├── api/
│   │   ├── UserSettingsController.java                ← NEW
│   │   └── dto/
│   │       ├── UserSettingsResponse.java              ← NEW
│   │       └── UserSettingsUpdateRequest.java         ← NEW
│   ├── application/
│   │   └── UserSettingsService.java                   ← NEW
│   ├── domain/
│   │   └── UserSettings.java                          ← NEW (record)
│   └── data/
│       ├── UserSettingsRepository.java                ← NEW (interface)
│       └── DataClientUserSettingsRepository.java      ← NEW (adapter)
├── db/
│   ├── schema/
│   │   └── user_settings.yaml                         ← EXISTS
│   └── commands/
│       ├── user_settings.find_by_user_id.sql          ← NEW
│       └── user_settings.upsert.sql                   ← NEW
├── frontend/
│   ├── index.ts                                       ← NEW
│   ├── routes.tsx                                     ← NEW
│   └── pages/
│       └── UserSettingsPage.tsx                       ← NEW
└── mobile/
    ├── plugin.dart                                    ← NEW
    └── pages/
        └── user_settings_page.dart                    ← NEW
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| User can only read/write their own settings | `user_id` from `SessionAuthentication`, never from request body |
| `theme` must be `lite`, `rich`, `system`, or `null` | DTO validation annotation |
| `preferred_language` max 10 chars | DTO `@Size` constraint |
| `timezone` max 64 chars | DTO `@Size` constraint |
| Missing settings row → return defaults, not 404 | Service returns a default `UserSettings` when the SELECT returns empty |
| Upsert, not insert + update | Single SQL command reduces round trips |

---

## Dependencies

- `identity` module (session, `id_user` table in `opzuser`).
- `user_settings` table must be migrated in each company's `opzuser` (`module.yaml: migrations.backend: true`).
- No other modules required.

---

## GUI Metadata Design

### Screen: User Settings Page

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `preferred_language` | Language | `dropdown` | No | ISO 639-1 codes with display names (see constants) | Any authenticated user | Selection immediately previews translated UI chrome (no save needed for preview) |
| `timezone` | Timezone | `dropdown` | No | IANA timezone names grouped by region | Any authenticated user | Shows current time in selected timezone as a hint below the field |
| `theme` | Theme | `radio-group` | No | `lite` (Light), `rich` (Rich), `system` (Follow device) | Any authenticated user | Live preview: switches UI chrome on select, before save |
| `email_notifications_enabled` | Email Notifications | `toggle` | Yes | true / false | Any authenticated user | — |

### Metadata-Driven Rules

- `preferred_language` dropdown options (value + display name) come from the constants file — not fetched from the API. This ensures the language switcher works even before a network response.
- `timezone` dropdown is searchable (typeahead) — renders as a lookup field from a static list, not a plain `<select>`.
- `theme` live preview switches a CSS class on the root element immediately. On cancel/page-leave without saving, the preview is reverted.
- The Save button shows a spinner while the `PUT` call is in flight. On success, show a toast notification using the common toast component.
- No delete action — settings row is always upserted, never deleted by the user.

---

## Directory Placement

```
modules/user-settings/              ← New module folder (non-application-specific)
├── backend/
├── frontend/
│   └── pages/
│       └── UserSettingsPage.tsx    ← Page component; uses field primitives from common
└── mobile/
    └── pages/
        └── user_settings_page.dart

common/frontend/src/
├── fields/controls/
│   ├── DropdownField/              ← Reusable searchable dropdown (used for language + timezone)
│   ├── RadioGroupField/            ← Reusable radio group (used for theme selection)
│   └── ToggleField/               ← Reusable toggle/switch (used for notifications)
└── kernel/constants/
    └── localeConstants.ts          ← LANGUAGE_OPTIONS[], TIMEZONE_OPTIONS[] (static lists)

common/mobile/lib/
└── fields/
    ├── dropdown_field.dart
    ├── radio_group_field.dart
    └── toggle_field.dart
```

**Rules:**
- `LANGUAGE_OPTIONS` and `TIMEZONE_OPTIONS` arrays live in `common/frontend` — shared by any module that needs locale pickers, not only user-settings.
- `UserSettingsPage` must not contain inline option arrays. It imports from `common/kernel/constants/localeConstants.ts`.
- Theme tokens (colors, font scale per theme) are in `common/frontend/src/theme/tokens.ts`.

---

## Constants

### Backend (`modules/user-settings/backend/.../UserSettingsConstants.java`)

```java
public static final String DEFAULT_THEME                      = null; // fallback to platform gui.mode
public static final boolean DEFAULT_EMAIL_NOTIFICATIONS       = true;
public static final String THEME_LITE                         = "lite";
public static final String THEME_RICH                         = "rich";
public static final String THEME_SYSTEM                       = "system";
public static final int    LANGUAGE_CODE_MAX_LEN              = 10;
public static final int    TIMEZONE_MAX_LEN                   = 64;
```

### Frontend (`modules/user-settings/frontend/userSettingsConstants.ts`)

```typescript
export const USER_SETTINGS_ENDPOINT    = "/api/v1/opzhub/user-settings/profile";
export const SETTINGS_PAGE_HEADING     = "My Settings";
export const LANGUAGE_FIELD_LABEL      = "Language";
export const TIMEZONE_FIELD_LABEL      = "Timezone";
export const THEME_FIELD_LABEL         = "Theme";
export const NOTIFICATIONS_LABEL       = "Email Notifications";
export const SAVE_LABEL                = "Save Settings";
export const SAVE_SUCCESS_MSG          = "Settings saved successfully.";
export const THEME_OPTIONS = [
  { value: "lite",   label: "Light"        },
  { value: "rich",   label: "Rich"         },
  { value: "system", label: "Follow device"},
];
```

Language and timezone option arrays live in `common/frontend/src/kernel/constants/localeConstants.ts`:

```typescript
export const LANGUAGE_OPTIONS = [
  { value: "en", label: "English" },
  { value: "ta", label: "Tamil"   },
  { value: "hi", label: "Hindi"   },
  // … extend without touching user-settings module
];

export const TIMEZONE_OPTIONS = [
  { value: "Asia/Kolkata",    label: "India Standard Time (IST)"    },
  { value: "UTC",             label: "UTC"                           },
  { value: "America/New_York",label: "Eastern Time (ET)"            },
  // … full IANA list grouped by region
];
```

---

## Optimization, Performance & Memory

### Performance
- Settings page loads with a **single GET** — no multiple sequential calls.
- `PUT` upsert uses a single SQL statement (`ON CONFLICT DO UPDATE`) — no read-before-write.
- Language and timezone dropdowns are populated from **static constants** — no API call on field render.
- Timezone list is large (~600 entries). Render only visible options using virtual scrolling (e.g., `react-window` or Flutter's `ListView.builder`).

### Memory
- **React:** The `theme` preview state is held in a `useRef` pointing to the previous class. On unmount without save, the `useEffect` cleanup restores it — no global state mutation left behind.
- **Flutter:** `TextEditingController` is not needed here (dropdowns/toggles). Dispose `AnimationController` if used for theme preview transitions.
- **Backend:** The `UserSettings` domain record is an immutable Java `record` — no mutable state after construction.

### Optimization
- Save is triggered explicitly by the user — **no auto-save** to avoid unnecessary write load on `opzuser`.
- Debounce is not needed on save (button click) — only on search fields.
- Timezone current-time hint renders client-side using `Intl.DateTimeFormat` — no server call.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/modules/user-settings/`.
No test files under `modules/user-settings/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `UserSettingsServiceTest` | GET returns defaults when no row; PUT upserts; partial update preserves unchanged fields |
| `UserSettingsControllerTest` | `user_id` locked to session; field validation (theme enum, lang max 10) |
| `UserSettingsRepositoryTest` | Upsert SQL idempotent on repeat save |

### RBAC in DB

User settings is self-service — any authenticated user reads and writes their own row.
No role permission required beyond being logged in. `SessionAuthFilter` is the gate.

`@RequiresPermission` is **not** applied. ABAC equivalent is locking `user_id` to `SessionAuthentication`:

```java
@GetMapping("/profile")
public ResponseEntity<ApiEnvelope<UserSettingsResponse>> getSettings(
        SessionAuthentication auth) {
    return ok(settingsService.findByUserId(auth.getUserId()));
}

@PutMapping("/profile")
public ResponseEntity<ApiEnvelope<UserSettingsResponse>> updateSettings(
        @Valid @RequestBody UserSettingsUpdateRequest request,
        SessionAuthentication auth) {
    return ok(settingsService.upsert(request, auth.getUserId()));
}
```

`id_user_permission` rows cannot be used to block or elevate user-settings access —
it is always self-service for the session owner.

### Form Metadata in DB

Form `id = "user-settings.profile.edit"` registered in `id_field_definition`:

```sql
-- field_key: preferred_language  field_type: select    is_mandatory: false  display_order: 1
--   allowed_values: null  (loaded from LANGUAGE_OPTIONS constant — static, not DB-driven)
-- field_key: timezone            field_type: select    is_mandatory: false  display_order: 2
--   allowed_values: null  (loaded from TIMEZONE_OPTIONS constant)
-- field_key: theme               field_type: radio     is_mandatory: false  display_order: 3
--   allowed_values: [{"value":"lite","label":"Light"},{"value":"rich","label":"Rich"},
--                    {"value":"system","label":"Follow device"}]
-- field_key: email_notifications_enabled  field_type: boolean  is_mandatory: true  display_order: 4
--   default_value: "true"
```

`allowed_values` for `preferred_language` and `timezone` is `null` in the DB —
these are static lists loaded from `localeConstants.ts` / `locale_constants.dart`
to keep the DB rows stable and avoid constant migration churn for locale lists.

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `GET /profile` | `SessionAuthFilter` (401) | None (self-service) | `user_id` from session only |
| `PUT /profile` | `SessionAuthFilter` (401) | None (self-service) | `user_id` from session only |

No 403 scenarios — either authenticated (200/400) or not (401).

### Coding Standards (this feature)

**Java:** `UserSettings` is an immutable `record`. Constants in `UserSettingsConstants.java`. All SQL in `modules/user-settings/db/commands/`.

**Flutter:** `AnimationController` for theme preview disposed in `dispose()`. No `TextEditingController` (dropdown/toggle fields). Constants in `UserSettingsConstants` class.

**TypeScript/React:** Theme CSS class change stored in `useRef` for cleanup on unmount without save. `LANGUAGE_OPTIONS` and `TIMEZONE_OPTIONS` imported from `common/frontend/src/kernel/constants/localeConstants.ts`.

### Directory Confirmation

```
modules/user-settings/                         ← new non-application module
    backend/.../api/UserSettingsController.java
    frontend/pages/UserSettingsPage.tsx
    mobile/pages/user_settings_page.dart
common/frontend/src/kernel/constants/
    localeConstants.ts                         ← LANGUAGE_OPTIONS[], TIMEZONE_OPTIONS[]
common/frontend/src/fields/controls/
    DropdownField/                             ← searchable dropdown (shared)
    RadioGroupField/                           ← radio group (shared)
    ToggleField/                               ← toggle/switch (shared)
```
