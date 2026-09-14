# Direct database access from Java (no HTTP) — usage guide

For code running **inside** `opzhub-be-app` (kernel or a module) that needs to
read/write the database directly, without going through the public
`/api/v1/opzhub/db/{entity}/*` REST surface documented in
[HELP-GENERIC-CRUD-API.md](HELP-GENERIC-CRUD-API.md).

There are always exactly two ways to touch the database from Java — the kernel
never allows raw SQL scattered through module code (doc 04 §3, doc 07 §3.1):

1. **Named commands** — a hand-written `.sql` file, executed by name. Use this
   whenever you have real business logic (validation beyond generic CRUD,
   joins, a query with return shape a table doesn't map 1:1 to).
2. **The generic schema engine** — no SQL file at all; a table declared once as
   `db/schema/<table>.yaml` gets create/read/update/delete/filter for free.
   Use this for straightforward CRUD-shaped data with no bespoke logic.

Both go through the same port, `DataClient`
(`common/backend/src/main/java/com/managemyopz/kernel/data/client/DataClient.java`):

```java
public interface DataClient {
    boolean ping();
    int execute(String statement, Map<String, Object> params);
    List<Row> query(String statement, Map<String, Object> params);
    Optional<Row> queryOne(String statement, Map<String, Object> params);
    <T> T transaction(Isolation isolation, Function<DataClient, T> work);
    DataClient withContext(Map<String, Object> context);
    int executeRaw(String sql, Map<String, Object> params);      // generic-engine escape hatch, see below
    List<Row> queryRaw(String sql, Map<String, Object> params);  // "
}
```

`Row` is an opaque `record Row(Map<String, Object> values)` with typed getters
(`getString`, `getLong`, `getBoolean`, `getDecimal`, `getInstant`) — never a raw
JDBC `ResultSet`.

## Getting the right `DataClient`

Most code needs the **default** connection — just `@Autowired`/constructor-inject
`DataClient` and use it; that's the connection declared under `platform.yaml`'s
`db:` block.

If your table lives on a **named** database (declared under `platform.yaml`'s
`databases:` block — today `OPZUSER`, `OPZMAIN`), inject
`DataClientRegistry` instead and resolve it once:

```java
public class MyRepository {
    private final DataClient dataClient;
    public MyRepository(DataClientRegistry registry) {
        this.dataClient = registry.forDatabase("OPZMAIN");
    }
}
```

`forDatabase(name)` never throws — an unconfigured name, or `db.type=memory`,
transparently falls back to the single default/shared client, so your code
doesn't need an `if (memory mode)` branch anywhere. This is exactly the
pattern `IdentityAutoConfiguration` uses to route identity's repository at
`OPZUSER` (`common/backend/.../data/client/DataClientRegistry.java`).

If you already have an `EntitySchema` (see below) instead of a literal database
name, call `registry.resolve(schema)` instead — it reads the schema's own
`database:` field for you.

## Option 1 — named commands (the classic pattern)

This is how `identity` reads/writes `id_user` (`modules/identity/backend/.../data/DataClientIdentityRepository.java`).

**1. Write the SQL** under `modules/<your-module>/db/commands/<name>.sql`, using
`:paramName`-style placeholders — never string concatenation:

```sql
-- modules/mymodule/db/commands/mymodule.find_active_by_region.sql
SELECT id, name, region FROM my_table WHERE region = :region AND active = true;
```

**2. Register it on the classpath** by adding a resources entry to
`common/backend/pom.xml`'s `add-module-resources` execution (mirror any
existing module's entry — `db/commands/**/*.sql` is what `CommandCatalog`
globs for).

**3. Call it** through whichever `DataClient` you resolved above:

```java
List<Row> rows = dataClient.query("mymodule.find_active_by_region", Map.of("region", "west"));
for (Row row : rows) {
    String name = row.getString("name");
    // ...
}

int updated = dataClient.execute("mymodule.deactivate", Map.of("id", id));

Optional<Row> one = dataClient.queryOne("mymodule.find_by_id", Map.of("id", id));
```

Named commands are how you get anything beyond plain CRUD: joins, aggregates,
`RETURNING` clauses with custom shapes, conditional logic in SQL — anything a
straight `WHERE column = value` filter can't express.

## Option 2 — the generic schema engine, called directly

If a table already has a `db/schema/*.yaml` (or you're about to add one — see
"Adding a new entity" in [HELP-GENERIC-CRUD-API.md](HELP-GENERIC-CRUD-API.md)),
you can reuse the exact same builder the REST layer uses instead of hand-writing
SQL, from `common/backend/.../kernel/data/schema/`:

```java
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.SchemaRegistry;

public class MyService {
    private final SchemaRegistry schemaRegistry;
    private final DataClientRegistry dataClientRegistry;

    public MyService(SchemaRegistry schemaRegistry, DataClientRegistry dataClientRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.dataClientRegistry = dataClientRegistry;
    }

    public Map<String, Object> createCompany(Map<String, Object> row) {
        EntitySchema schema = schemaRegistry.require("company_information"); // throws DataClientException.notFound if unknown or expose_generic_api:false
        DataClient client = dataClientRegistry.resolve(schema);

        SqlCommand command = GenericSqlBuilder.insert(schema, row);
        Row inserted = client.queryRaw(command.sql(), command.params()).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Insert returned no row"));
        return new LinkedHashMap<>(inserted.values());
    }
}
```

`GenericSqlBuilder` also has `selectById`, `selectFiltered` (takes a
`com.managemyopz.kernel.data.schema.web.FilterSpec`), `count`, `update`, and
`delete` — the exact same methods `GenericCrudRepository` calls. Every one
validates payload/filter keys against `schema.columnNames()` before building
any SQL, so an unknown field throws `IllegalArgumentException` immediately
rather than reaching the database.

**Note on `expose_generic_api: false`**: `SchemaRegistry.require(entity)`
enforces that flag — calling it from your own module code for an entity like
`id_user` will throw `not_found` exactly like the REST layer does. That flag
protects the HTTP surface *and* accidental in-process misuse from other
modules; identity's own code doesn't call `require()` at all — it uses its own
named commands (Option 1) and never goes through the generic engine.

**Validation**: if you're accepting external input into a generic-engine write,
run it through `GenericValidationEngine` first (the same required/type/max-length
checks the REST layer applies) rather than skipping straight to `GenericSqlBuilder`:

```java
List<SchemaValidationException.FieldError> errors = validationEngine.validate(schema, row, /* isPatch= */ false);
if (!errors.isEmpty()) {
    throw new SchemaValidationException(errors);
}
```

## Transactions

Both options share the same transaction port — wrap multiple statements in one
`DataClient.transaction(...)` call so they commit or roll back together:

```java
dataClient.transaction(Isolation.READ_COMMITTED, tx -> {
    tx.execute("mymodule.debit", Map.of("id", fromId, "amount", amount));
    tx.execute("mymodule.credit", Map.of("id", toId, "amount", amount));
    return null;
});
```

Use `tx` (the callback argument) inside the lambda if you want to be explicit
about which client the work runs on, but note today's `PostgresDataServer`
implementation passes back the same instance it was called on (`work.apply(this)`)
— either `tx` or the outer `dataClient` variable works identically inside the
callback, since Spring's transaction manager binds the transaction to the
current thread, not to a specific object reference.

`Isolation.SERIALIZABLE` is available for the rare case that needs it (e.g.
posting/period-close); `READ_COMMITTED` is the default everywhere else.

## Testing your code

- **Unit tests**: mock `DataClient` (or `DataClientRegistry`) with Mockito —
  see `GenericCrudServiceTest`/`SchemaReconcilerTest` for the established
  pattern (stub `queryRaw`/`executeRaw`/`transaction` return values, verify
  calls with `ArgumentCaptor`).
- **`db.type=memory`**: for named commands, register your query/execute
  handlers on `MemoryCommandRegistry` (see `IdentityMemoryStore` for the
  pattern) so your module still works without Postgres in dev/test. The
  generic schema engine does **not** support memory mode yet — a generic-engine
  write against a `db.type=memory` deployment throws
  `DataClientException.unsupported(...)`; if your module needs to work in
  memory mode, use named commands (Option 1) for now.
