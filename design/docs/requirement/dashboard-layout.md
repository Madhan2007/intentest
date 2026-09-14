# Requirement: Dashboard Layout Configuration

**Module:** `dashboard-layout` (new — common, non-app-specific)
**DB target:** `opzmain` (widget catalog, templates) + `opzhub` per-company (active layouts, user overrides)
**Access level:** Admin configures role layouts; any authenticated user may personalise within admin-allowed bounds
**Architecture ref:** [doc 34](../architecture/34-dashboard-layout-design.md)

---

## What It Does

Provides a metadata-driven, drag-and-drop dashboard configuration system shared by **all** ManageMyOpz applications. Admins define which widgets appear in which zones for each role. Developers register widgets in a catalog YAML — no layout is hardcoded in any frontend or backend file.

Key behaviours:
- Admin opens a visual layout editor, drags widgets from a palette, drops them into zones (header, sidebar, main grid, quick-actions, footer).
- Admin can apply a named template as a starting point, then customise further.
- Each role gets its own layout per application.
- Users may personalise their own view within the bounds the admin defined (move/resize only; cannot add widgets that are not in their role layout).
- Layout is merged at runtime: user override → role layout → platform default template.
- Every widget entry in the catalog includes role-based visibility control, icon, default size, and a props schema for widget-level configuration.

---

## DB Schema

**Database:** `opzmain` for catalog and templates; `opzhub` for per-company active layouts.

### `widget_catalog` — in `opzmain`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `widget_key` | text | No | — | PK, e.g. `kpi-revenue` |
| `app_key` | text | No | — | Target app or `*` for all apps |
| `widget_name` | text | No | — | Display name in the editor palette |
| `description` | text | Yes | null | Tooltip text in the palette |
| `icon_key` | text | Yes | null | Resolved from `common/frontend/src/theme/icons/` |
| `default_zone` | text | No | — | Suggested drop zone |
| `default_w` | int | No | — | Default grid width (1-12) |
| `default_h` | int | No | — | Default grid height in rows |
| `min_w` | int | No | — | Minimum resize width |
| `min_h` | int | No | — | Minimum resize height |
| `props_schema` | jsonb | Yes | null | JSON Schema for widget-level config props |
| `allowed_roles` | text[] | No | `{}` | Empty = all roles may use this widget |
| `is_system` | boolean | No | `false` | System widgets cannot be removed by admin |
| `status` | text | No | `'active'` | `active` \| `deprecated` |
| `created_at` | timestamptz | No | `now()` | — |

### `dashboard_template` — in `opzmain`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `template_key` | text | No | — | PK, e.g. `hr-manager-default` |
| `app_key` | text | No | — | App scope or `*` |
| `template_name` | text | No | — | Human label shown in template picker |
| `description` | text | Yes | null | — |
| `thumbnail_uri` | text | Yes | null | Preview image shown in template picker modal |
| `is_builtin` | boolean | No | `true` | Seed-managed; admins cannot delete |
| `created_by` | uuid | Yes | null | null for built-in; user UUID for admin-created |
| `created_at` | timestamptz | No | `now()` | — |

### `dashboard_template_widget` — in `opzmain`

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | uuid | No | PK `gen_random_uuid()` |
| `template_key` | text | No | FK → `dashboard_template` |
| `widget_key` | text | No | FK → `widget_catalog` |
| `zone_key` | text | No | Zone identifier (see Zone table below) |
| `position` | jsonb | No | `{ "x": 0, "y": 0, "w": 6, "h": 3 }` |
| `widget_props` | jsonb | Yes | Instance-level config overrides |

### `dashboard_layout` — in `opzhub` (per-company)

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | uuid | No | PK |
| `company_id` | uuid | No | FK → `opzmain.company_information(id)` |
| `app_key` | text | No | App this layout applies to |
| `role_key` | text | No | Role slug, e.g. `admin`, `manager` |
| `template_key` | text | Yes | Last applied template (audit) |
| `layout_data` | jsonb | No | Full layout JSON (see Layout Format below) |
| `updated_by` | uuid | No | FK → `opzuser.id_user(id)` |
| `updated_at` | timestamptz | No | `now()` |
| UNIQUE | — | — | `(company_id, app_key, role_key)` |

### `dashboard_layout_user` — in `opzhub` (per-company)

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | uuid | No | PK |
| `user_id` | uuid | No | FK → `opzuser.id_user(id)` |
| `company_id` | uuid | No | — |
| `app_key` | text | No | — |
| `layout_data` | jsonb | No | Same structure as `dashboard_layout.layout_data` |
| `updated_at` | timestamptz | No | `now()` |
| UNIQUE | — | — | `(user_id, company_id, app_key)` |

---

## Zones

| Zone Key | Position | Typical Content |
|----------|----------|----------------|
| `header_bar` | Top full-width strip | KPI pills, notification badge |
| `sidebar_top` | Upper sidebar | Navigation quick-links, favourites |
| `sidebar_bottom` | Lower sidebar | Settings, support shortcuts |
| `main_grid` | Central body | Charts, tables, KPI cards |
| `quick_actions` | Floating / fixed strip | Primary CTA buttons per role |
| `footer_bar` | Bottom strip | System status, last-sync time |

Zones are opt-in per app. An app with no sidebar simply omits `sidebar_top` and `sidebar_bottom` from its widget catalog entries.

---

## Layout Data Format

```json
{
  "zones": {
    "header_bar": [
      { "widget_key": "kpi-revenue",   "x": 0, "y": 0, "w": 4, "h": 1, "props": {} },
      { "widget_key": "kpi-headcount", "x": 4, "y": 0, "w": 4, "h": 1, "props": {} }
    ],
    "main_grid": [
      { "widget_key": "revenue-chart", "x": 0, "y": 0, "w": 8, "h": 4,
        "props": { "period": "monthly" } },
      { "widget_key": "recent-orders", "x": 8, "y": 0, "w": 4, "h": 4, "props": {} }
    ],
    "quick_actions": [
      { "widget_key": "action-new-sale", "x": 0, "y": 0, "w": 2, "h": 1, "props": {} }
    ]
  },
  "grid_cols": 12,
  "grid_row_height": 80
}
```

Grid uses a 12-column system. `x`, `y` are zero-based. `w` and `h` are span counts.

---

## API Endpoints

Base path: `/api/v1/opzhub/dashboard-layout`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/catalog?app_key=` | Authenticated | List all widgets available to caller's role for the app |
| `GET` | `/templates?app_key=` | Authenticated | List available templates (name, thumbnail, description) |
| `GET` | `/layout?app_key=&role_key=` | Admin | Read active role layout |
| `PUT` | `/layout` | Admin | Save role layout (full replace) |
| `POST` | `/layout/apply-template` | Admin | Apply a template to a role (resets then saves) |
| `GET` | `/my-layout?app_key=` | Authenticated | Read merged layout for the calling user |
| `PUT` | `/my-layout` | Authenticated | Save user personalisation override |
| `DELETE` | `/my-layout?app_key=` | Authenticated | Reset personalisation → fall back to role layout |

### GET /catalog response (200 OK)
```json
{
  "ok": true,
  "data": {
    "widgets": [
      {
        "widget_key": "kpi-revenue",
        "widget_name": "Revenue KPI",
        "icon_key": "icon-revenue",
        "default_zone": "header_bar",
        "default_w": 3, "default_h": 1,
        "min_w": 2, "min_h": 1,
        "props_schema": {},
        "is_system": false
      }
    ]
  },
  "correlation_id": "..."
}
```

### PUT /layout request body
```json
{
  "app_key": "manage-my-finance",
  "role_key": "manager",
  "layout_data": { "zones": { ... }, "grid_cols": 12, "grid_row_height": 80 }
}
```

### POST /layout/apply-template request body
```json
{
  "app_key": "manage-my-finance",
  "role_key": "manager",
  "template_key": "finance-manager-default"
}
```

### Merge algorithm (GET /my-layout)

```
1. Load role layout for (company_id, app_key, primary_role)
2. Load user override for (user_id, company_id, app_key)
3. If user override exists:
   a. Remove any widget_key not present in the role layout (security trim)
   b. Apply user positions for remaining matched widgets
4. Return merged layout (role layout if no valid override)
```

---

## Business Rules

- Widget catalog entries are global (in `opzmain`); layout configs are per-company.
- Admin may only manage layouts for their own `company_id`.
- Super Admin may manage widget catalog entries and templates across all companies.
- Users may only move or resize widgets already in their role layout — they cannot add or remove widgets.
- Widgets with `is_system = true` cannot be removed from a layout by the admin editor; they appear locked in the UI.
- `allowed_roles = []` means all roles may use the widget. A non-empty array restricts to the listed roles.
- Layout data is validated against the widget catalog on save — unknown `widget_key` values are rejected with `400 Bad Request`.
- The merge service trims any user override widgets that no longer exist in the role layout (e.g., admin removed a widget after user personalised it).

---

## Widget Registration (developer guide)

Each application registers its widgets in:

```
modules/dashboard-layout/db/seed/templates.yaml
```

Template entry example (HR app):

```yaml
widget_catalog:
  - widget_key: "hr-headcount-chart"
    app_key: "manage-my-hr"
    widget_name: "Headcount Overview"
    description: "Active vs inactive staff by department"
    icon_key: "icon-people-bar"
    default_zone: "main_grid"
    default_w: 6
    default_h: 3
    min_w: 4
    min_h: 2
    allowed_roles: ["admin", "hr-manager"]
    is_system: false

dashboard_templates:
  - template_key: "hr-manager-default"
    app_key: "manage-my-hr"
    template_name: "HR Manager Default"
    description: "Headcount KPIs, leave calendar, and quick hire action"
    is_builtin: true
    widgets:
      - widget_key: "kpi-headcount"
        zone_key: "header_bar"
        position: { x: 0, y: 0, w: 3, h: 1 }
      - widget_key: "hr-headcount-chart"
        zone_key: "main_grid"
        position: { x: 0, y: 0, w: 6, h: 3 }
      - widget_key: "action-new-hire"
        zone_key: "quick_actions"
        position: { x: 0, y: 0, w: 2, h: 1 }
```

`DashboardTemplateSeed.java` reads this YAML on startup and upserts all entries.

---

## GUI Metadata Design

### Admin Layout Editor screen

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `app_key` | Application | dropdown | Yes | Values from app catalog | Admin | Changing app reloads catalog and current role layout |
| `role_key` | Role | dropdown | Yes | Roles from `id_role` for company | Admin | Changing role loads that role's layout into the canvas |
| `template_key` | Apply Template | lookup-modal | No | Values from `dashboard_template` | Admin | Opens `TemplatePicker` modal; applying resets canvas with template widgets |
| `zone_key` | Zone | visual-drop-target | No | Enum of zone keys | Admin | Highlights on drag-over; invalid drop targets show red border |
| `widget_entry` | Widget | draggable-card | No | Values from `widget_catalog` | Admin | Drag from palette to zone; resize handle on placed widget; trash icon to remove |
| `widget_props` | Widget Config | props-form | No | Per-widget JSON Schema | Admin | Opens inline props drawer when clicking ⚙ on a placed widget |
| `grid_cols` | Grid Columns | number | No | 8 \| 12 \| 16 | Admin | Changes grid density; triggers re-layout validation |
| `grid_row_height` | Row Height (px) | number | No | 60–120 | Admin | Adjusts row height visually |

### User Personalisation screen (within allowed widgets)

| Field Key | Heading | Type | Mandatory | Role Access | Subactions |
|-----------|---------|------|-----------|-------------|-----------|
| `widget_position` | Drag to rearrange | draggable-card | No | Authenticated | Move within same zone only |
| `widget_resize` | Resize widget | resize-handle | No | Authenticated | Constrained by min_w/min_h from catalog |
| `reset_layout` | Reset to default | action-button | No | Authenticated | Confirm dialog → DELETE /my-layout |

### Template Picker modal

| Field Key | Heading | Type | Notes |
|-----------|---------|------|-------|
| `template_key` | Select Template | card-grid | Each card shows thumbnail, name, description |
| `preview` | Preview | overlay | Hovering a card shows a read-only zone preview |

All field metadata is sourced from `widget_catalog.props_schema` and served via `GET /catalog`. The frontend never hardcodes field labels, allowed values, or mandatory flags.

---

## Directory Placement

```
modules/dashboard-layout/               ← common module (not app-specific)
├── module.yaml
├── backend/src/main/java/com/managemyopz/modules/dashboardlayout/
│   ├── api/
│   │   ├── DashboardLayoutController.java       ← /layout, /my-layout, /apply-template
│   │   └── DashboardCatalogController.java      ← /catalog, /templates
│   ├── application/
│   │   ├── DashboardLayoutService.java
│   │   ├── DashboardMergeService.java           ← merge algorithm
│   │   └── DashboardTemplateSeed.java           ← startup YAML upsert
│   ├── data/
│   │   ├── WidgetCatalogRepository.java
│   │   ├── DashboardTemplateRepository.java
│   │   ├── DashboardLayoutRepository.java
│   │   └── DashboardLayoutUserRepository.java
│   └── domain/
│       ├── WidgetEntry.java                     ← value object (widget_key, x, y, w, h, props)
│       └── DashboardLayoutConstants.java
├── db/
│   ├── schema/
│   │   ├── widget_catalog.yaml
│   │   ├── dashboard_template.yaml
│   │   ├── dashboard_template_widget.yaml
│   │   ├── dashboard_layout.yaml
│   │   └── dashboard_layout_user.yaml
│   └── seed/
│       └── templates.yaml                       ← built-in templates per app
└── frontend/src/
    ├── pages/
    │   ├── DashboardLayoutEditor.tsx            ← admin drag-drop config page
    │   └── TemplatePicker.tsx                  ← template selection modal
    ├── components/
    │   ├── WidgetPalette.tsx                    ← draggable widget list (sidebar)
    │   ├── DropZone.tsx                         ← zone drop target (highlights on drag)
    │   ├── WidgetFrame.tsx                      ← resizable widget shell
    │   ├── WidgetPropsDrawer.tsx                ← per-widget config panel
    │   └── LayoutGrid.tsx                       ← 12-col grid renderer
    └── hooks/
        ├── useDashboardLayout.ts               ← fetches + caches layout
        └── useWidgetCatalog.ts                 ← fetches + caches catalog

common/frontend/src/
├── components/dashboard/
│   ├── DashboardRenderer.tsx                   ← reusable renderer used by every app
│   └── WidgetRegistry.ts                       ← maps widget_key → React component
├── theme/
│   ├── icons/                                  ← icon registry for icon_key resolution
│   └── dashboard.tokens.css                    ← grid gap, zone border, shadow vars
└── constants/
    └── dashboardConstants.ts                   ← zone keys, grid cols, row height defaults

common/mobile/lib/modules/dashboard_layout/
├── widgets/
│   ├── widget_frame.dart
│   └── layout_grid.dart
└── providers/
    └── dashboard_layout_provider.dart
```

Every application's dashboard page imports `DashboardRenderer` from `common/frontend`:

```tsx
// apps/manage-my-finance/frontend/src/pages/FinanceDashboard.tsx
import { DashboardRenderer } from 'common/components/dashboard/DashboardRenderer';

export default function FinanceDashboard() {
  return <DashboardRenderer appKey="manage-my-finance" />;
}
```

No layout logic lives in the application page — only `DashboardRenderer` calls the API and renders.

---

## Constants

### Backend Java — `DashboardLayoutConstants.java`

```java
public final class DashboardLayoutConstants {
    public static final int    GRID_COLS_DEFAULT    = 12;
    public static final int    GRID_ROW_HEIGHT_PX   = 80;
    public static final int    WIDGET_MIN_W         = 1;
    public static final int    WIDGET_MIN_H         = 1;
    public static final int    LAYOUT_CACHE_TTL_SEC = 600;   // 10 min
    public static final int    USER_CACHE_TTL_SEC   = 300;   // 5 min
    public static final int    CATALOG_CACHE_TTL_SEC= 1800;  // 30 min
    public static final String APP_KEY_WILDCARD     = "*";
    public static final String ZONE_HEADER_BAR      = "header_bar";
    public static final String ZONE_SIDEBAR_TOP     = "sidebar_top";
    public static final String ZONE_SIDEBAR_BOTTOM  = "sidebar_bottom";
    public static final String ZONE_MAIN_GRID       = "main_grid";
    public static final String ZONE_QUICK_ACTIONS   = "quick_actions";
    public static final String ZONE_FOOTER_BAR      = "footer_bar";
    public static final String STATUS_ACTIVE        = "active";
    public static final String STATUS_DEPRECATED    = "deprecated";
    public static final String ERR_UNKNOWN_WIDGET   = "unknown_widget_key";
    public static final String ERR_ZONE_INVALID     = "invalid_zone_key";
}
```

### Frontend TypeScript — `dashboardConstants.ts`

```ts
export const DASHBOARD = {
  GRID_COLS:       12,
  ROW_HEIGHT_PX:   80,
  ZONES: ['header_bar','sidebar_top','sidebar_bottom',
          'main_grid','quick_actions','footer_bar'] as const,
  API: {
    CATALOG:        '/api/v1/opzhub/dashboard-layout/catalog',
    TEMPLATES:      '/api/v1/opzhub/dashboard-layout/templates',
    LAYOUT:         '/api/v1/opzhub/dashboard-layout/layout',
    APPLY_TEMPLATE: '/api/v1/opzhub/dashboard-layout/layout/apply-template',
    MY_LAYOUT:      '/api/v1/opzhub/dashboard-layout/my-layout',
  },
  CACHE_TTL_MS: {
    CATALOG: 30 * 60_000,
    LAYOUT:  10 * 60_000,
    USER:     5 * 60_000,
  },
} as const;
```

Icon keys, zone labels, and default props are sourced only from the catalog API — never hardcoded in components.

---

## RBAC Controls

| Permission | Module | Feature | Action | Holder |
|-----------|--------|---------|--------|--------|
| View own merged layout | `dashboard-layout` | `my-layout` | `r` | All authenticated |
| Personalise own layout | `dashboard-layout` | `my-layout` | `u` | All authenticated |
| Reset own layout | `dashboard-layout` | `my-layout` | `d` | All authenticated |
| View role layout | `dashboard-layout` | `role-layout` | `r` | Admin, Company Admin |
| Save role layout | `dashboard-layout` | `role-layout` | `u` | Admin, Company Admin |
| Apply template to role | `dashboard-layout` | `role-layout` | `u` | Admin, Company Admin |
| Create / edit templates | `dashboard-layout` | `templates` | `cud` | Super Admin |
| Manage widget catalog | `dashboard-layout` | `catalog` | `crud` | Super Admin |

ABAC: Admin can only manage layouts for their own `company_id`. Super Admin is unrestricted.

Controller enforcement:
```java
@RequiresPermission(module = "dashboard-layout", feature = "role-layout", action = "u")
@PutMapping("/layout")
public ResponseEnvelope<Void> saveLayout(...) { ... }
```

---

## Optimization, Performance & Memory

### Performance
- Widget catalog and templates are read-heavy, write-rare → Valkey cache with 30-min TTL; invalidated on Super Admin catalog update.
- Role layout cached per `(company_id, app_key, role_key)` in Valkey, 10-min TTL; invalidated immediately on admin save.
- User override cached per `(user_id, app_key)`, 5-min TTL.
- Merge runs in Java in-memory — no extra DB round trip when both cache entries hit.
- `layout_data` JSONB uses a GIN index on the `zones` key for future widget-search queries.
- Catalog `GET /catalog` response is paged if > 100 widgets; `?page=&size=` supported.

### Memory
- React: `useDashboardLayout` registers an `AbortController`; cancelled on component unmount.
- DnD drag state lives in local component state only — never in a global store to avoid stale references.
- Flutter: `DashboardLayoutProvider` calls `dispose()` releasing stream subscriptions.
- Widget instances are rendered lazily — off-screen widgets in a scrollable zone use `ListView.builder` (Flutter) / virtual scroll (React).

### Optimization
- `PUT /layout` accepts the full layout JSONB in one call (`UPSERT … ON CONFLICT DO UPDATE`) — no per-widget round trips.
- `GET /my-layout` returns the merged layout directly; no second request needed by the client.
- Widget catalog is seeded on startup with UPSERT so re-deploys are safe and idempotent.
- Template thumbnails are static URIs served from the CDN/static server — not generated at runtime.
- `WidgetRegistry.ts` uses dynamic `import()` (React lazy) so only the widget components actually present in the user's layout are loaded.

---

## Standard Implementation Rules

See [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) for rules on:
- Unit test location (`managemyopz-testing/01-unit/modules/dashboard-layout/`)
- RBAC in DB (`id_role`, `id_user_role`, `id_role_permission`, `id_user_permission`)
- Format/mandatory metadata from DB (`id_field_definition`) — widget catalog `props_schema` doubles as the field definition source for widget config drawers
- RBAC/ABAC enforcement at API level (`@RequiresPermission` + `PermissionCheckAspect`)
