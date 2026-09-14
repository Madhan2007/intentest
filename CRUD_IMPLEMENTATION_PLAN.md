# MANAGEMYOPZ — MASTER CRUD API ARCHITECTURE & IMPLEMENTATION PLAN

---

# 1. Executive Summary

This master plan provides the definitive, implementation-ready architectural blueprint for the Core CRUD API layer in **ManageMyOPZ**.

ManageMyOPZ is a modular ERP platform (Java 21, Spring Boot 3.3.4, PostgreSQL 16) built on strict hexagonal/ports-and-adapters principles. A thorough read-only investigation of the entire codebase confirmed that:
1. Persistence is strictly abstracted through the kernel `DataClient` port. Spring Data JPA, Hibernate, and dynamic string-concatenated SQL are **strictly forbidden** by architectural mandate (`doc 04 §3`, `doc 07 §3.1`).
2. All database interactions execute via static named SQL commands stored in `modules/*/db/commands/*.sql` and loaded by `CommandCatalog`.
3. Web communication is uniformly enveloped in `ApiEnvelope<T>`, with correlation IDs tracked by `CorrelationFilter` and stateless session authentication enforced by `SessionAuthFilter`.
4. **No line-of-business entities, tables, or business fields currently exist in the repository.** The only existing table is `id_user` in the `identity` module.

**Architectural Determination:**
The CRUD architecture, connection management, single-step execution, validation flow, security model, and transaction boundaries are **100% resolved and locked**. The implementation status is **IMPLEMENTATION READY AFTER SPECIFIC CONFIRMATIONS**; the only missing element is the senior developer's selection of the target business entity and its table schema.

---

# 2. Repository Findings

| Dimension | Discovered Technical Reality | Evidence in Repository |
| :--- | :--- | :--- |
| **Java Version** | **Java 21** (`<java.version>21</java.version>`) with Virtual Threads enabled via `Executors.newVirtualThreadPerTaskExecutor()`. | `common/backend/pom.xml`, `VirtualThreadConfig.java` |
| **Spring Boot** | **3.3.4** (`spring-boot-starter-parent`). | `common/backend/pom.xml` |
| **Build System** | Apache Maven. Feature module Java sources (`modules/<id>/backend/src/main/java`) are dynamically mounted into `opzhub-be-app` via `build-helper-maven-plugin`. | `common/backend/pom.xml` (lines 90–135) |
| **ORM / JPA** | **None.** Spring Data JPA and Hibernate are not in `pom.xml` and are explicitly prohibited. | `design/docs/architecture/04-backend-java-design.md` §3 |
| **Data Engine** | `PostgresDataServer` wrapping `NamedParameterJdbcTemplate` and a self-managed `HikariDataSource` (pool min: 2, max: 8). Standard Spring `DataSourceAutoConfiguration` is disabled. | `KernelApplication.java`, `PostgresDataServer.java`, `platform.yaml` |
| **SQL Loading** | Named commands scanned from `classpath*:modules/*/db/commands/*.sql` by `CommandCatalog.java`. Statement names match filenames without `.sql`. | `CommandCatalog.java`, `DataServerConstants.java` |
| **Web Enveloping** | `ApiEnvelope<T>` (`ok: boolean`, `data: T`, `error: ApiError`, `correlation_id: String`). | `ApiEnvelope.java` |
| **Correlation** | `CorrelationFilter.java` captures/generates `X-Correlation-ID`, populates SLF4J MDC, and sets request attribute `correlation_id`. | `CorrelationFilter.java` |
| **Exception Handling** | `KernelExceptionHandler.java` maps `DataClientException` (404, 409, 503), `AccessDeniedException` (403), and `Exception` (500). Lacks a handler for `MethodArgumentNotValidException`. | `KernelExceptionHandler.java` |
| **Security** | `SessionAuthFilter.java` resolves session tokens from `opzhub_session` cookie or `Bearer` header against `CacheClient`. `SecurityConfig.java` enforces `anyRequest().authenticated()`. | `SecurityConfig.java`, `SessionAuthFilter.java` |
| **Existing DDL & Tables**| Only `modules/identity/db/0001_identity_init.sql` creating table `id_user`. Zero line-of-business tables. | `modules/identity/db/` |
| **Domain Models** | Only `com.managemyopz.modules.identity.domain.User` (an immutable record). Zero line-of-business models. | `User.java` |
| **Testing Tools** | `spring-boot-starter-test` & `spring-security-test` in `pom.xml`. In-memory execution supported by `MemoryDataServer` and `MemoryCommandRegistry`. | `pom.xml`, `MemoryDataServer.java` |

---

# 3. Confirmed Facts

### A. Confirmed from Codebase
* Architecture: Port/Adapter pattern with `DataClient` interface and `PostgresDataServer` implementation.
* Database Access: Named SQL commands loaded via `CommandCatalog` using `NamedParameterJdbcTemplate`.
* Connection Pool: HikariCP (pool_min: 2, pool_max: 8) configured in `platform.yaml`.
* Response Format: `ApiEnvelope<T>` with mandatory `correlation_id`.
* Request Tracking: `X-Correlation-ID` header processed via `CorrelationFilter`.
* Authentication: Stateless session authentication via `SessionAuthFilter` (`opzhub_session` / `Bearer`).
* Authorization: Session matrix letters `c` (create), `v` (view/read), `u` (update), `d` (delete) via `SessionAuthentication.getMatrix()`.
* Existing Schema: Only `id_user` exists. No business entities exist.

### B. Confirmed by Senior
* Required Operations: CREATE, READ, READ BY ID, UPDATE, DELETE, and basic validation.
* Technology: Java + PostgreSQL.
* Supplied Sample Endpoints: `POST /api/db/create`, `POST /api/db/read`, `PUT /api/db/update`, `DELETE /api/db/delete`.
* Supplied Class Structure: `CrudController`, `CrudService`, `CreateRequest`, `UpdateRequest`, `DeleteRequest`, `ReadRequest`.
* Performance Directive: Must work efficiently and avoid putting unnecessary load on PostgreSQL.
* Entity Constraint: Do NOT assume CREATE means users/admins; entity must be grounded in requirements.

---

# 4. Existing Architecture

The flow follows the verified ManageMyOPZ runtime pipeline:

```text
HTTP Request (Web SPA / Flutter)
      │
      │  Header: X-Correlation-ID, Cookie: opzhub_session / Bearer token
      ▼
CorrelationFilter
  - Extracts/generates correlation ID; sets MDC & request attribute
      │
      ▼
SessionAuthFilter & SecurityConfig
  - Validates session token against CacheClient; sets SecurityContext
      │
      ▼
CrudController (com.managemyopz.modules.<module>.api)
  - Enforces @Valid on inbound DTO records
  - Extracts correlation_id from request attribute
  - Calls CrudService
  - Wraps response in ApiEnvelope<T>
      │
      ▼
CrudService (com.managemyopz.modules.<module>.application)
  - Manages transaction boundaries via DataClient.transaction(...)
  - Executes single-step updates and deletes
  - Handles domain business rules & existence validation
  - Converts DTO records to/from Domain records
      │
      ▼
CrudRepository & DataClientCrudRepository (com.managemyopz.modules.<module>.data)
  - Invokes named SQL commands on DataClient
  - Binds parameter maps (Map<String, Object>)
  - Maps DataClient Row objects to Domain records
      │
      ▼
DataClient -> PostgresDataServer (com.managemyopz.kernel.data)
  - Resolves command name to SQL text via CommandCatalog
  - Acquires connection from Hikari pool (max 8)
  - Executes via NamedParameterJdbcTemplate
      │
      ▼
PostgreSQL 16 Database
```

---

# 5. CRUD Requirement Interpretation

The senior developer provided sample endpoints:
`POST /api/db/create`, `POST /api/db/read`, `PUT /api/db/update`, `DELETE /api/db/delete`.

We interpret this as:
1. **Core Responsibility:** Build the production-grade, database-efficient foundation for persistent state management upon which web application features depend.
2. **Endpoint Purpose:** The `/api/db/*` paths represent the **functional contract** for the 5 assigned operations.
3. **Architecture Fit:** It must use the project's standard `DataClient` engine, connection pool, and `ApiEnvelope` response wrapper while adhering to the senior's method names and class layout.

---

# 6. Entity Discovery

### Investigation Results:
* **Domain Models:** Searched all packages; only `User.java` exists.
* **Database Scripts:** Searched all `.sql` files; only `0001_identity_init.sql` (`id_user`) and 4 user commands exist.
* **Frontend Code:** Searched `common/frontend/src` and `modules/`; found only `LoginPage.tsx` and text field primitives (`TextField`, `PasswordField`).
* **Catalog:** `platform/catalog/applications.yaml` mentions prospective future modules (`master-data`, `ledger`, `inventory`, `documents`, `hr`, `ticketing`), but none of their folders, tables, or DDL scripts exist.

### Conclusion:
**No business entity currently exists in the codebase.**
In strict accordance with Rule 1 (*Do NOT invent business requirements*), we do not invent a dummy entity (e.g. `Resource`, `Product`, `Customer`). The target business entity is an open decision that must be confirmed by the senior developer.

---

# 7. Generic vs Entity-Specific CRUD Decision

### Evaluation of Options:
1. **Option 1: Truly Generic Database Gateway (`{"table": "...", "data": { ... }}`):**
   * *Critical Security Risk:* Severe SQL injection risk; allows clients to target internal tables (`id_user`, audit tables); completely bypasses column-level authorization and PostgreSQL Row-Level Security.
   * *Architectural Violation:* Architecture doc `07-data-cache-client-server.md` §3.1 explicitly forbids dynamic table names and dynamic SQL concatenation.
   * *Validation Failure:* Impossible to apply static Jakarta Bean Validation annotations.
2. **Option 2: Entity-Specific CRUD via Sample Endpoint Naming (RECOMMENDED):**
   * Exposes the senior's sample endpoints (`/api/db/*`) bound to a strongly typed DTO, domain record, and static named SQL commands.
   * Fully preserves static typing, parameter binding, compile-time safety, and Bean Validation.
3. **Option 3: Modular REST Convention (`/api/v1/opzhub/<module>/<entity>`):**
   * Aligns with the rest of the application routes (e.g. `/api/v1/opzhub/identity/login`).

### Recommendation:
Implement **Option 2** as the primary contract (preserving the exact endpoints assigned by the senior developer), with the internal code structured so that binding to **Option 3** is a simple route annotation change. Dynamic database operations are strictly rejected.

---

# 8. Final Recommended API Contract

All endpoints require authentication and return standard `ApiEnvelope<T>`.

### 1. CREATE — `POST /api/db/create`
* **Method & URI:** `POST /api/db/create`
* **Authentication:** Required (`opzhub_session` cookie or `Authorization: Bearer <token>`).
* **Request Body:** `CreateRequest` (JSON with confirmed entity fields).
* **Validation:** `@Valid` on DTO fields.
* **Success Status:** `201 Created`
* **Response Body:** `ApiEnvelope<EntityResponse>` containing generated ID and created fields.
* **Failure Responses:** `400 Bad Request` (validation failure), `401 Unauthorized`, `409 Conflict` (duplicate natural key), `503 Service Unavailable`.

### 2. READ (List) — `POST /api/db/read` (without `id`)
* **Method & URI:** `POST /api/db/read`
* **Authentication:** Required.
* **Request Body:** `ReadRequest` (`{ "page": 0, "size": 20 }` — `id` is null).
* **Validation:** `@Min(0)` on `page`, `@Min(1) @Max(100)` on `size`.
* **Success Status:** `200 OK`
* **Response Body:** `ApiEnvelope<PageResponse<EntityResponse>>` (`items`, `page`, `size`, `total_items`, `total_pages`, `has_more`).
* **Empty Result:** Returns `200 OK` with `items: []`, `total_items: 0`, `has_more: false`.

### 3. READ BY ID — `POST /api/db/read` (with `id`)
* **Method & URI:** `POST /api/db/read`
* **Authentication:** Required.
* **Request Body:** `ReadRequest` (`{ "id": "<id_value>" }`).
* **Behavior:** When `id` is non-null, service routes directly to `findById(id)`.
* **Success Status:** `200 OK`
* **Response Body:** `ApiEnvelope<EntityResponse>` for the single record.
* **Failure Responses:** `404 Not Found` (if record does not exist), `400 Bad Request` (invalid ID format).

### 4. UPDATE — `PUT /api/db/update`
* **Method & URI:** `PUT /api/db/update`
* **Authentication:** Required.
* **Request Body:** `UpdateRequest` (`{ "id": "<id_value>", "<confirmed_fields>": "..." }`).
* **Validation:** `@NotNull` on `id`, validation constraints on updated fields.
* **Success Status:** `200 OK`
* **Response Body:** `ApiEnvelope<EntityResponse>` containing updated record.
* **Failure Responses:** `404 Not Found`, `400 Bad Request`, `409 Conflict`.

### 5. DELETE — `DELETE /api/db/delete`
* **Method & URI:** `DELETE /api/db/delete`
* **Authentication:** Required.
* **Request Body:** `DeleteRequest` (`{ "id": "<id_value>" }`).
* **Validation:** `@NotNull` on `id`.
* **Success Status:** `200 OK`
* **Response Body:** `ApiEnvelope<Map<String, Object>>` (`{ "deleted": true, "id": "..." }`).
* **Failure Responses:** `404 Not Found`, `400 Bad Request`.

---

# 9. DTO Design

All DTOs are implemented as immutable Java records in `dto/`:

```java
// CreateRequest.java
public record CreateRequest(
    // Specific fields pending entity confirmation.
    // E.g.: @NotBlank String code, @NotBlank String title
) {}

// ReadRequest.java
public record ReadRequest(
    String id, // Optional: if provided, triggers READ BY ID
    @Min(value = 0, message = "page must be 0 or greater") Integer page,
    @Min(value = 1, message = "size must be at least 1")
    @Max(value = 100, message = "size cannot exceed 100") Integer size
) {
    public ReadRequest {
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}

// UpdateRequest.java
public record UpdateRequest(
    @NotNull(message = "id is required") String id
    // Modifiable fields pending entity confirmation
) {}

// DeleteRequest.java
public record DeleteRequest(
    @NotNull(message = "id is required") String id
) {}

// PageResponse.java
public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long total_items,
    int total_pages,
    boolean has_more
) {}
```

---

# 10. Controller Design

Implemented in `controller/CrudController.java`:

### Responsibilities:
* Annotate class with `@RestController` and `@RequestMapping("/api/db")`.
* Enforce Bean Validation via `@Valid @RequestBody`.
* Retrieve `correlation_id` from `request.getAttribute(CorrelationFilter.MDC_KEY)`.
* Delegate to `CrudService`.
* Wrap results in `ApiEnvelope.ok(data, correlationId)`.
* Return HTTP `201 Created` for create, `200 OK` for read/update/delete.

### What Controller Must NOT Do:
* No SQL logic, JDBC calls, or `DataClient` access.
* No transaction management.
* No domain business validation.

---

# 11. Service Design

Implemented in `service/CrudService.java`:

### Responsibilities:
* Manage transaction boundaries: read operations run auto-commit; multi-query writes run in `dataClient.transaction(Isolation.READ_COMMITTED, tx -> ...)`.
* Handle READ vs READ BY ID branching based on `request.id()`.
* **Single-Step UPDATE:** Call repository to execute update; inspect affected rows. If 1 -> return updated entity; if 0 -> run fast existence check (`SELECT 1`) to distinguish `404 Not Found` from a no-op identical update.
* **Single-Step DELETE:** Call repository to execute delete; inspect affected rows. If 1 -> return success; if 0 -> throw `DataClientException.notFound("Record not found")`.
* Convert DTO records to/from Domain records.

---

# 12. Repository / DataClient Design

Implemented in `data/CrudRepository.java` (Interface) and `data/DataClientCrudRepository.java` (Adapter):

### Responsibilities:
* Injects `com.managemyopz.kernel.data.client.DataClient`.
* Invokes static named SQL commands registered in `CommandCatalog`:
  * `<entity>.insert`
  * `<entity>.find_by_id`
  * `<entity>.list_paged`
  * `<entity>.count_total`
  * `<entity>.update`
  * `<entity>.delete`
* Maps parameters via `Map<String, Object>`.
* Maps `Row` instances to Domain records using typed extractors (`row.getString(...)`, `row.getLong(...)`, `row.getUuid(...)`).

---

# 13. PostgreSQL Design

*DDL will be created under `modules/<module>/db/0001_<module>_init.sql` once the entity is confirmed:*

```text
Table Name   → PENDING CONFIRMATION
Primary Key  → UUID (DEFAULT gen_random_uuid())
Columns      → PENDING CONFIRMATION
Constraints  → PRIMARY KEY (id), UNIQUE on natural key if applicable
Indexes      → B-tree on id (PK), B-tree on sort column
```

### Static SQL Command Templates (Ready for Entity Binding)
* `<entity>.insert.sql`: `INSERT INTO <table> (id, ...) VALUES (:id, ...) RETURNING id, ...;`
* `<entity>.find_by_id.sql`: `SELECT id, ... FROM <table> WHERE id = :id;`
* `<entity>.list_paged.sql`: `SELECT id, ... FROM <table> ORDER BY id ASC LIMIT :limit OFFSET :offset;`
* `<entity>.count_total.sql`: `SELECT COUNT(*) AS total_count FROM <table>;`
* `<entity>.update.sql`: `UPDATE <table> SET col1 = :col1, col2 = :col2 WHERE id = :id;`
* `<entity>.delete.sql`: `DELETE FROM <table> WHERE id = :id;`

---

# 14. Validation Design

Validation is structured in two tiers without redundant database load:

1. **Request Validation (Jakarta Validation on DTOs):**
   * Enforced in controller via `@Valid`.
   * Validates non-null presence, string lengths, ranges, and formats.
2. **Domain / Database Validation:**
   * Record existence verified by service inspecting affected rows or repository results.
   * Uniqueness and referential integrity enforced by PostgreSQL constraints at the ACID boundary (translating PostgreSQL error codes `23505` to HTTP 409).

---

# 15. Error Handling

All errors strictly follow the kernel `ApiEnvelope` format:

```json
{
  "ok": false,
  "data": null,
  "error": {
    "code": "error_code",
    "kind": "error_kind",
    "msg": "User-safe error message",
    "hint": null,
    "fields": null
  },
  "correlation_id": "c7a84e20-3b91-4c12-92e1-4710189b8832"
}
```

### Kernel Exception Handler Modification:
Add a handler for `MethodArgumentNotValidException` to `KernelExceptionHandler.java`:
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiEnvelope<Void>> onValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fe : e.getBindingResult().getFieldErrors()) {
        fields.put(fe.getField(), fe.getDefaultMessage());
    }
    Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.fail(
        new ApiEnvelope.ApiError("validation_failed", "validation_failed", "Invalid request parameters.", null, fields),
        correlationId == null ? "" : correlationId.toString()
    ));
}
```

### HTTP Status Mapping:
* `201 Created`: Successful CREATE.
* `200 OK`: Successful READ, READ BY ID, UPDATE, DELETE.
* `400 Bad Request`: Validation failure.
* `401 Unauthorized`: Missing/invalid session token.
* `403 Forbidden`: Insufficient RBAC permission letter.
* `404 Not Found`: Target ID does not exist.
* `409 Conflict`: Unique constraint violation (`DataClientException.conflict`).
* `503 Service Unavailable`: Database unreachable (`DataClientException.unavailable`).
* `500 Internal Error`: Unexpected runtime exceptions.

---

# 16. Security Design

1. **Authentication:** Enforced by `SessionAuthFilter` and `SecurityConfig` (`anyRequest().authenticated()`). Unauthenticated requests receive HTTP 401.
2. **Authorization (RBAC):** Verified via `SessionAuthentication.getMatrix()`. Enforces operation letters: `c` (create), `v` (view/read), `u` (update), `d` (delete).
3. **SQL Injection Protection:** 100% parameter binding through `NamedParameterJdbcTemplate`. Dynamic SQL string building is strictly forbidden.
4. **Mass Assignment Prevention:** DTO records expose only allowed fields; auto-generated columns (`id`) cannot be overwritten.
5. **No Data Leakage:** Database driver stack traces and credentials are never exposed in error responses.

---

# 17. Database Performance Strategy

| Metric | Design Choice | Performance Rationale |
| :--- | :--- | :--- |
| **CREATE Queries** | **1 query** (`INSERT ... RETURNING`) | Avoids preliminary `SELECT exists`; database UNIQUE constraint enforces uniqueness atomically. |
| **READ Queries** | **2 queries** (1 paged select + 1 count) | Projection avoids `SELECT *`; page size capped at 100; single `COUNT(*)` for metadata. |
| **READ BY ID** | **1 query** (`SELECT ... WHERE id = :id`) | Single indexed primary key lookup. |
| **UPDATE Queries** | **1 query** in normal flow (`UPDATE ... WHERE id = :id`) | Affected row count verifies update; only issues a second `SELECT 1` if 0 rows modified to verify 404. |
| **DELETE Queries** | **1 query** (`DELETE ... WHERE id = :id`) | Affected row count verifies deletion; eliminates preliminary `SELECT exists`. |
| **Connection Pool** | Managed HikariCP (min 2, max 8) | Short transactions; connections acquired only during repository execution and released immediately. |

---

# 18. Transaction Strategy

* **CREATE:** Wrapped in `dataClient.transaction(Isolation.READ_COMMITTED, tx -> ...)` if multiple statements are involved; otherwise single atomic insert.
* **READ & READ BY ID:** Read-only queries execute in auto-commit mode without an explicit transaction block, avoiding unnecessary lock acquisition.
* **UPDATE:** Single-statement update executes atomically. Wrapped in transaction if cross-table consistency is required.
* **DELETE:** Single-statement delete executes atomically.
* **Guiding Rule:** Non-database work (validation, DTO parsing, logging) is never performed inside transactions.

---

# 19. File-by-File Changes

### 1. Files to Modify

```text
FILE: common/backend/src/main/java/com/managemyopz/kernel/web/KernelExceptionHandler.java
ACTION: MODIFY
PURPOSE: Add global exception handling for Spring Bean Validation failures (MethodArgumentNotValidException).
CHANGES:
1. Add @ExceptionHandler(MethodArgumentNotValidException.class) method.
2. Build Map<String, String> of field errors from BindingResult.
3. Return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.fail(...)).
DEPENDENCIES: MethodArgumentNotValidException, ApiEnvelope, CorrelationFilter.
POTENTIAL IMPACT: Zero impact on existing endpoints; ensures validation failures return structured ApiEnvelope.
```

```text
FILE: common/backend/pom.xml
ACTION: MODIFY (Only if placing code in a new module folder)
PURPOSE: Register new module source and resource directories in build-helper-maven-plugin.
CHANGES:
1. Add <source> path under add-module-sources execution.
2. Add <resource> mapping under add-module-resources execution.
DEPENDENCIES: None.
POTENTIAL IMPACT: Enables compilation of the new module without affecting kernel.
```

### 2. Files to Create (Pending Entity Confirmation)

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/api/CrudController.java
ACTION: CREATE (BLOCKED ON ENTITY)
PURPOSE: REST controller exposing /api/db/create, /api/db/read, /api/db/update, /api/db/delete.
DEPENDENCIES: CrudService, CreateRequest, ReadRequest, UpdateRequest, DeleteRequest, ApiEnvelope.
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/api/dto/CreateRequest.java
ACTION: CREATE (BLOCKED ON ENTITY & FIELDS)
PURPOSE: Immutable record for CREATE input data with Bean Validation annotations.
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/api/dto/ReadRequest.java
ACTION: CREATE
PURPOSE: Immutable record for READ & READ BY ID input data (optional id, page, size).
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/api/dto/UpdateRequest.java
ACTION: CREATE (BLOCKED ON ENTITY & FIELDS)
PURPOSE: Immutable record for UPDATE input data (required id, modifiable fields).
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/api/dto/DeleteRequest.java
ACTION: CREATE
PURPOSE: Immutable record for DELETE input data (required id).
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/api/dto/PageResponse.java
ACTION: CREATE
PURPOSE: Uniform record wrapper for paginated list output.
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/application/CrudService.java
ACTION: CREATE (BLOCKED ON ENTITY)
PURPOSE: Business service managing transaction boundaries and single-step CRUD logic.
DEPENDENCIES: CrudRepository, DataClient, DTOs, Domain Record.
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/domain/<Entity>.java
ACTION: CREATE (BLOCKED ON ENTITY)
PURPOSE: Immutable domain record representing entity state (no JPA annotations).
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/data/CrudRepository.java
ACTION: CREATE (BLOCKED ON ENTITY)
PURPOSE: Repository interface declaring data access operations.
```

```text
FILE: modules/<module>/backend/src/main/java/com/managemyopz/modules/<module>/data/DataClientCrudRepository.java
ACTION: CREATE (BLOCKED ON ENTITY)
PURPOSE: Repository adapter invoking named SQL commands via kernel DataClient.
DEPENDENCIES: DataClient, CrudRepository, Domain Record, Row.
```

```text
FILE: modules/<module>/db/0001_<module>_init.sql
ACTION: CREATE (BLOCKED ON ENTITY & SCHEMA)
PURPOSE: PostgreSQL DDL migration script for creating table and indexes.
```

```text
FILES: modules/<module>/db/commands/<entity>.*.sql (insert, find_by_id, list_paged, count_total, update, delete)
ACTION: CREATE (BLOCKED ON ENTITY)
PURPOSE: Named SQL commands loaded by CommandCatalog.
```

### 3. Files to Leave Unchanged
* `common/backend/src/main/java/com/managemyopz/kernel/data/client/DataClient.java`
* `common/backend/src/main/java/com/managemyopz/kernel/data/server/PostgresDataServer.java`
* `common/backend/src/main/java/com/managemyopz/kernel/data/server/CommandCatalog.java`
* `common/backend/src/main/java/com/managemyopz/kernel/security/*`
* `platform/config/platform.yaml`

---

# 20. Method-Level Implementation Plan

### `CrudController`
* `ResponseEntity<ApiEnvelope<EntityResponse>> create(@Valid @RequestBody CreateRequest request, HttpServletRequest httpRequest)`
* `ResponseEntity<ApiEnvelope<Object>> read(@Valid @RequestBody ReadRequest request, HttpServletRequest httpRequest)`
* `ResponseEntity<ApiEnvelope<EntityResponse>> update(@Valid @RequestBody UpdateRequest request, HttpServletRequest httpRequest)`
* `ResponseEntity<ApiEnvelope<Map<String, Object>>> delete(@Valid @RequestBody DeleteRequest request, HttpServletRequest httpRequest)`

### `CrudService`
* `EntityRecord create(CreateRequest request)`: Enforces business uniqueness, calls repository insert, returns created record.
* `Object read(ReadRequest request)`: If `request.id() != null`, calls `findById(request.id())`; else calls `findAllPaged(request.page(), request.size())` and `countTotal()`.
* `EntityRecord findById(String id)`: Calls repository; throws `DataClientException.notFound(...)` if empty.
* `EntityRecord update(UpdateRequest request)`: Calls repository update; checks affected rows; throws `404` if not found; returns updated record.
* `void delete(String id)`: Calls repository delete; checks affected rows; throws `404` if not found.

### `CrudRepository`
* `EntityRecord insert(EntityRecord entity)`
* `Optional<EntityRecord> findById(String id)`
* `List<EntityRecord> findAllPaged(int limit, int offset)`
* `long countTotal()`
* `int update(EntityRecord entity)`
* `int delete(String id)`
* `boolean existsById(String id)`

---

# 21. SQL Command Plan

*All commands will be placed in `modules/<module>/db/commands/`:*

| Command Name | SQL File | Purpose | Parameters | Returns |
| :--- | :--- | :--- | :--- | :--- |
| `<entity>.insert` | `<entity>.insert.sql` | Atomic insert | All entity columns | Created row (`RETURNING *` or explicit columns) |
| `<entity>.find_by_id` | `<entity>.find_by_id.sql` | Primary key lookup | `:id` | Single row or empty |
| `<entity>.list_paged` | `<entity>.list_paged.sql` | Bounded pagination | `:limit`, `:offset` | Ordered list of rows |
| `<entity>.count_total` | `<entity>.count_total.sql` | Total count for metadata | None | Single numeric value (`total_count`) |
| `<entity>.update` | `<entity>.update.sql` | Single-step mutation | `:id`, updated fields | Affected row count |
| `<entity>.delete` | `<entity>.delete.sql` | Single-step deletion | `:id` | Affected row count |

---

# 22. Testing Plan

### 1. Controller Slice Tests (`CrudControllerTest` — `@WebMvcTest`)
* Test valid CREATE returns `201 Created` with `ApiEnvelope.ok`.
* Test invalid CREATE (blank/null fields) returns `400 Bad Request` with field error map.
* Test READ (list) returns `200 OK` with paginated structure.
* Test READ BY ID returns `200 OK` when record exists; returns `404 Not Found` when missing.
* Test UPDATE returns `200 OK` on success; returns `404 Not Found` when ID missing.
* Test DELETE returns `200 OK` on success; returns `404 Not Found` when ID missing.
* Verify `X-Correlation-ID` is present on all responses.

### 2. Service Unit Tests (`CrudServiceTest` — JUnit 5 + Mockito)
* Verify transaction boundaries and DTO-to-domain mapping.
* Verify UPDATE handles 1 vs 0 affected rows.
* Verify DELETE handles 1 vs 0 affected rows.

### 3. Repository Tests (`CrudRepositoryTest` — `MemoryDataServer`)
* Verify command execution, parameter mapping, and `Row` extraction using `MemoryDataServer` without Docker.

### 4. PostgreSQL Integration Verification
* Verify end-to-end execution, indexes, and constraints against PostgreSQL using `platform.yaml` dev configuration.

---

# 23. Implementation Order

```text
1. Senior confirms target business entity, table name, and columns.
2. Modify KernelExceptionHandler.java to add MethodArgumentNotValidException handler.
3. If new module folder: Update common/backend/pom.xml to add module source/resource roots.
4. Create PostgreSQL migration script in modules/<module>/db/0001_<module>_init.sql.
5. Create named SQL command files in modules/<module>/db/commands/<entity>.*.sql.
6. Create domain record in modules/<module>/backend/.../domain/<Entity>.java.
7. Create DTO records in modules/<module>/backend/.../api/dto/.
8. Create repository interface and DataClient adapter in modules/<module>/backend/.../data/.
9. Create CrudService in modules/<module>/backend/.../application/.
10. Create CrudController in modules/<module>/backend/.../api/.
11. Implement unit and slice tests (CrudControllerTest, CrudServiceTest, CrudRepositoryTest).
12. Run 'mvn clean test' to verify build and test suite.
13. Run end-to-end API verification against PostgreSQL.
```

---

# 24. Definition of Done

- [ ] Target business entity and table schema confirmed by senior developer.
- [ ] No invented business entities, tables, or fields exist in the code.
- [ ] Endpoints `/api/db/create`, `/api/db/read`, `/api/db/update`, `/api/db/delete` are mapped and functioning.
- [ ] `POST /api/db/read` handles both paginated list read and READ BY ID (via `id` in payload).
- [ ] All responses and errors return enveloped in `ApiEnvelope<T>` with `correlation_id`.
- [ ] Jakarta Bean Validation handles field constraints and returns `400 Bad Request` with field map.
- [ ] Authentication is verified via `SessionAuthFilter` (returns `401 Unauthorized` if unauthenticated).
- [ ] Persistence strictly uses `DataClient` with named SQL commands in `CommandCatalog` (no JPA/Hibernate, no dynamic SQL).
- [ ] Database performance optimizations active:
  - CREATE uses single `INSERT ... RETURNING` (1 query).
  - READ BY ID uses single indexed PK query (1 query).
  - READ list uses bounded `LIMIT/OFFSET` with max 100 + single `COUNT(*)` (2 queries).
  - UPDATE uses single-step update, inspecting affected rows (1 query in normal path).
  - DELETE uses single-step delete, inspecting affected rows (1 query).
- [ ] All SQL commands use named parameter binding (`:paramName`).
- [ ] Unit and controller slice tests pass (`mvn test`).
- [ ] Clean build passes without compiler warnings or architectural violations.

---

# 25. Remaining Decisions

The following minimum questions must be answered by the senior developer:

| # | Question for Senior Developer | Why It Matters | What Cannot Be Implemented Until Answered | Recommended Default |
| :- | :--- | :--- | :--- | :--- |
| **1** | What is the target business entity name and owning module? | Dictates module folder and domain record name. | Entity record, package structure, and module registration. | Master Data module (`master-data`). |
| **2** | What is the PostgreSQL table name, columns, and data types? | Dictates SQL commands, DDL, and DTO fields. | DDL script, DTOs, SQL files, and validation rules. | Standard UUID PK, confirmed business columns. |
| **3** | Should READ BY ID be invoked via `POST /api/db/read` (with `id` in payload) or via `GET /api/db/read/{id}`? | Dictates controller endpoint mapping. | Controller method signature. | Support `POST /api/db/read` with optional `id` as primary, alias `GET /{id}`. |
| **4** | Should deletion be physical hard deletion or logical soft deletion? | Dictates whether soft-delete columns and query filters are required. | Delete SQL command and table DDL. | Hard delete (`DELETE FROM <table> WHERE id = :id`). |

---

# 26. FINAL STATUS

```text
FINAL STATUS:
IMPLEMENTATION READY AFTER SPECIFIC CONFIRMATIONS

RECOMMENDED NEXT ACTION:
Ask the senior developer the 4 specific questions in Section 25. Once she confirms the target business entity and schema, proceed directly with Step 1 of Section 23 (Implementation Order).
```
