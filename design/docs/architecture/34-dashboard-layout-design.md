# 34 — Dashboard Layout Design: Configurable Widget Placement Across All Apps

## 1. Overview

The Dashboard Layout system provides a unified, metadata-driven mechanism for every application in ManageMyOpz to define what components (widgets) appear on its dashboard, where they appear, and which roles may see or interact with them.

Dashboard configuration is done by admins through a drag-and-drop interface backed by a database-stored layout model. Developers do not hardcode layouts — the layout engine reads widget placement from the DB at runtime.

| Concern | Approach |
|---------|---------|
| Widget catalog | DB table `widget_catalog` — registered once per widget type per app |
| Layout templates | DB table `dashboard_template` — named presets (e.g., "HR Manager Default") |
| Active layout | DB table `dashboard_layout` — per-company, per-app, per-role active config |
| User overrides | DB table `dashboard_layout_user` — per-user personalisation within admin-allowed bounds |
| Storage format | JSONB columns holding grid positions and widget-level props |
| UI config tool | Drag-and-drop layout editor (React `@dnd-kit/core`; Flutter equivalent) |
| RBAC | Widget visibility, add/remove, and drag rights gated by role grants in `id_role_permission` |

---

## 2. Concepts

### 2.1 Widget

A **Widget** is any self-contained UI component that can be placed on a dashboard: a KPI card, a chart, a quick-action shortcut, a recent-activity list, a notification badge, etc.

Each widget is registered in `widget_catalog` with a unique `widget_key`, a target `app_key` (or `*` for cross-app), and its metadata (title, icon, default size, props schema, roles that may use it).

### 2.2 Zone

A **Zone** is a named area of the dashboard page where widgets can be dropped.

| Zone Key | Position | Typical content |
|----------|----------|----------------|
| `header_bar` | Top full-width strip | Company name, global KPI pills, notification bell |
| `sidebar_top` | Upper sidebar | Navigation quick-links, favourites |
| `sidebar_bottom` | Lower sidebar | Support, settings shortcuts |
| `main_grid` | Central body | Charts, tables, KPI cards — primary area |
| `quick_actions` | Floating/fixed strip | Primary CTA buttons per role |
| `footer_bar` | Bottom strip | System status, version, last-sync time |

Zones are defined per-app in the widget catalog YAML seed. Not every app has every zone.

### 2.3 Template

A **Template** is a named, shareable layout preset. Admins apply a template to a role, which populates `dashboard_layout` with that template's widget-zone-position data as a starting point. They can then drag and adjust further.

Built-in templates are seeded from `modules/dashboard-layout/db/seed/templates.yaml`.

### 2.4 Layout Hierarchy

```
Platform seed templates  (YAML → widget_catalog, dashboard_template)
        ↓  admin applies template to a role
Company-Role Layout      (dashboard_layout — per company, per app, per role)
        ↓  user personalises within allowed widgets
User Layout Override     (dashboard_layout_user — per user)
        ↓  merged at render time
Rendered Dashboard
```

If no user override exists, the role layout is used. If no role layout exists, the platform default template for that app is used.

---

## 3. Database Schema

**Database:** `opzhub` (per-company) for layout config; `opzmain` for widget catalog and templates (shared across companies).

### 3.1 `widget_catalog` — in `opzmain`

Central registry of all available widget types.

```
widget_catalog
├── widget_key       TEXT  PK          Unique key, e.g., "kpi-revenue", "hr-headcount-chart"
├── app_key          TEXT  NOT NULL    App scope, e.g., "manage-my-finance", or "*" for all apps
├── widget_name      TEXT  NOT NULL    Display name shown in the layout editor palette
├── description      TEXT  NULLABLE    Short description for the editor tooltip
├── icon_key         TEXT  NULLABLE    Icon identifier resolved from common/frontend/src/theme/icons/
├── default_zone     TEXT  NOT NULL    Suggested zone for first-time placement
├── default_w        INT   NOT NULL    Default width in grid columns (e.g., 4 of 12)
├── default_h        INT   NOT NULL    Default height in grid rows (e.g., 2)
├── min_w            INT   NOT NULL    Minimum width (resize constraint)
├── min_h            INT   NOT NULL    Minimum height (resize constraint)
├── props_schema     JSONB NULLABLE    JSON Schema for widget-level configuration props
├── allowed_roles    TEXT[]            Roles that may add this widget; empty = all roles
├── is_system        BOOLEAN DEFAULT false   System widgets cannot be removed
├── status           TEXT  DEFAULT 'active'  active | deprecated
├── created_at       TIMESTAMPTZ DEFAULT now()
```

Index: `idx_widget_catalog_app_key ON widget_catalog(app_key)`

### 3.2 `dashboard_template` — in `opzmain`

Named layout presets. Each template has a header row and N widget rows.

```
dashboard_template
├── template_key     TEXT  PK          e.g., "hr-manager-default", "finance-overview"
├── app_key          TEXT  NOT NULL    App this template belongs to (or "*")
├── template_name    TEXT  NOT NULL    Display name, e.g., "HR Manager Default"
├── description      TEXT  NULLABLE
├── thumbnail_uri    TEXT  NULLABLE    Preview image shown in template picker
├── is_builtin       BOOLEAN DEFAULT true    Built-in templates are seed-managed
├── created_by       UUID  NULLABLE    NULL for built-in; user UUID for custom
├── created_at       TIMESTAMPTZ DEFAULT now()
```

```
dashboard_template_widget
├── id               UUID  PK DEFAULT gen_random_uuid()
├── template_key     TEXT  NOT NULL    FK → dashboard_template(template_key)
├── widget_key       TEXT  NOT NULL    FK → widget_catalog(widget_key)
├── zone_key         TEXT  NOT NULL    Zone where placed in this template
├── position         JSONB NOT NULL    { "x": 0, "y": 0, "w": 6, "h": 3 }
├── widget_props     JSONB NULLABLE    Instance-level props overrides
```

Index: `idx_dtw_template_key ON dashboard_template_widget(template_key)`

### 3.3 `dashboard_layout` — in `opzhub` (per-company)

The active, admin-configured layout for a company+app+role combination.

```
dashboard_layout
├── id               UUID  PK DEFAULT gen_random_uuid()
├── company_id       UUID  NOT NULL    FK → opzmain.company_information(id)
├── app_key          TEXT  NOT NULL    App this layout applies to
├── role_key         TEXT  NOT NULL    Role slug, e.g., "admin", "manager", "viewer"
├── template_key     TEXT  NULLABLE    Last applied template (audit trail)
├── layout_data      JSONB NOT NULL    Full layout: { zones: { zone_key: [{ widget_key, x, y, w, h, props }] } }
├── updated_by       UUID  NOT NULL    User who last saved the layout
├── updated_at       TIMESTAMPTZ DEFAULT now()
├── UNIQUE (company_id, app_key, role_key)
```

### 3.4 `dashboard_layout_user` — in `opzhub` (per-company)

Per-user personalisation layer. Only widgets present in the role layout may appear here.

```
dashboard_layout_user
├── id               UUID  PK DEFAULT gen_random_uuid()
├── user_id          UUID  NOT NULL    FK → opzuser.id_user(id)
├── company_id       UUID  NOT NULL
├── app_key          TEXT  NOT NULL
├── layout_data      JSONB NOT NULL    Same structure as dashboard_layout.layout_data
├── updated_at       TIMESTAMPTZ DEFAULT now()
├── UNIQUE (user_id, company_id, app_key)
```

---

## 4. Layout Data Format

The `layout_data` JSONB column uses a consistent structure understood by both the React and Flutter renderers:

```json
{
  "zones": {
    "header_bar": [
      { "widget_key": "kpi-revenue",    "x": 0, "y": 0, "w": 4, "h": 1, "props": {} },
      { "widget_key": "kpi-headcount",  "x": 4, "y": 0, "w": 4, "h": 1, "props": {} }
    ],
    "main_grid": [
      { "widget_key": "revenue-chart",  "x": 0, "y": 0, "w": 8, "h": 4, "props": { "period": "monthly" } },
      { "widget_key": "recent-orders",  "x": 8, "y": 0, "w": 4, "h": 4, "props": {} }
    ],
    "quick_actions": [
      { "widget_key": "action-new-sale","x": 0, "y": 0, "w": 2, "h": 1, "props": {} }
    ]
  },
  "grid_cols": 12,
  "grid_row_height": 80
}
```

Grid coordinates use a 12-column system by default. `x`, `y` are zero-based.

---

## 5. API Endpoints

All paths under `/api/v1/opzhub/dashboard-layout`.

| Method | Path | Role required | Purpose |
|--------|------|--------------|---------|
| `GET` | `/catalog?app_key=` | Authenticated | List all available widgets for the app |
| `GET` | `/templates?app_key=` | Authenticated | List available templates |
| `GET` | `/layout?app_key=&role_key=` | Admin | Read role layout |
| `PUT` | `/layout` | Admin | Save role layout (full replace) |
| `POST`| `/layout/apply-template` | Admin | Apply a template to a role (resets layout) |
| `GET` | `/my-layout?app_key=` | Authenticated | Read merged layout for calling user |
| `PUT` | `/my-layout` | Authenticated | Save user personalisation override |
| `DELETE` | `/my-layout?app_key=` | Authenticated | Reset user override → fall back to role layout |

### Merge algorithm (`GET /my-layout`)

```
1. Load role layout for (company_id, app_key, primary_role)
2. Load user override for (user_id, company_id, app_key)
3. If user override exists:
   a. For each widget in user override, verify widget_key exists in role layout
   b. Remove any user widget not in role catalog (security trim)
   c. Return merged layout (user positions override role positions for matched keys)
4. Else return role layout as-is
```

---

## 6. Module Placement

```
modules/dashboard-layout/
├── module.yaml
├── backend/
│   └── src/main/java/com/managemyopz/modules/dashboardlayout/
│       ├── api/
│       │   ├── DashboardLayoutController.java
│       │   └── DashboardCatalogController.java
│       ├── application/
│       │   ├── DashboardLayoutService.java
│       │   ├── DashboardMergeService.java       ← merge algorithm
│       │   └── DashboardTemplateSeed.java       ← seeds widget_catalog + templates on startup
│       ├── data/
│       │   ├── WidgetCatalogRepository.java
│       │   ├── DashboardTemplateRepository.java
│       │   ├── DashboardLayoutRepository.java
│       │   └── DashboardLayoutUserRepository.java
│       └── domain/
│           ├── WidgetEntry.java                 ← value object for one widget in layout
│           └── DashboardLayoutConstants.java
├── db/
│   ├── schema/
│   │   ├── widget_catalog.yaml
│   │   ├── dashboard_template.yaml
│   │   ├── dashboard_template_widget.yaml
│   │   ├── dashboard_layout.yaml
│   │   └── dashboard_layout_user.yaml
│   └── seed/
│       └── templates.yaml                       ← built-in templates per app
└── frontend/
    └── src/
        ├── pages/
        │   ├── DashboardLayoutEditor.tsx         ← admin drag-drop editor
        │   └── TemplatePicker.tsx               ← template selection modal
        ├── components/
        │   ├── WidgetPalette.tsx                 ← draggable widget list
        │   ├── DropZone.tsx                      ← zone drop target
        │   ├── WidgetFrame.tsx                   ← resizable widget shell
        │   └── LayoutGrid.tsx                    ← 12-col grid renderer
        └── hooks/
            ├── useDashboardLayout.ts
            └── useWidgetCatalog.ts

common/frontend/src/
├── theme/
│   ├── icons/                                    ← widget icon registry (shared)
│   └── dashboard.tokens.css                      ← grid gap, zone border, shadow vars
└── constants/
    └── dashboardConstants.ts                     ← zone keys, grid cols, row height

common/mobile/lib/
├── modules/dashboard_layout/
│   ├── widgets/
│   │   ├── widget_frame.dart
│   │   └── layout_grid.dart
│   └── hooks/
│       └── dashboard_layout_provider.dart
```

---

## 7. Widget Registration Pattern

Each application registers its widgets by adding entries to `modules/dashboard-layout/db/seed/templates.yaml`:

```yaml
widget_catalog:
  - widget_key: "hr-headcount-chart"
    app_key: "manage-my-hr"
    widget_name: "Headcount Overview"
    description: "Bar chart of active vs inactive staff by department"
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
      - widget_key: "leave-calendar"
        zone_key: "main_grid"
        position: { x: 6, y: 0, w: 6, h: 3 }
      - widget_key: "action-new-hire"
        zone_key: "quick_actions"
        position: { x: 0, y: 0, w: 2, h: 1 }
```

---

## 8. RBAC Controls

| Permission | Module | Feature | Action | Who holds it |
|-----------|--------|---------|--------|-------------|
| View own dashboard layout | `dashboard-layout` | `my-layout` | `r` | All authenticated |
| Personalise own layout | `dashboard-layout` | `my-layout` | `u` | All authenticated |
| View role layouts | `dashboard-layout` | `role-layout` | `r` | Admin, Company Admin |
| Save role layout | `dashboard-layout` | `role-layout` | `u` | Admin, Company Admin |
| Apply template | `dashboard-layout` | `role-layout` | `u` | Admin, Company Admin |
| Manage widget catalog | `dashboard-layout` | `catalog` | `crud` | Super Admin |
| Manage templates | `dashboard-layout` | `templates` | `crud` | Super Admin |

ABAC rule: Admin can only manage layouts for their own `company_id`. Super Admin can manage all.

---

## 9. Performance & Caching

- Widget catalog: cached per app_key in Valkey on first load, TTL 30 min. Invalidated on catalog update.
- Role layout: cached per `(company_id, app_key, role_key)` in Valkey, TTL 10 min. Invalidated on admin save.
- User override: cached per `(user_id, app_key)`, TTL 5 min. Invalidated on user save or reset.
- Merge operation runs in-memory in Java — no extra DB call if both cache entries hit.
- `layout_data` JSONB is indexed with GIN on `zones` key for future search-by-widget queries.
