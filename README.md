# Webstore Management REST API

A Java Spring Boot backend for the **Database Solutions** course, built around the course-provided relational `webstore` schema in MariaDB. The project implements product and category management, customer lookup/creation, transactional order placement, catalog reporting, and product price-change history.

> **Status:** Functional coursework prototype. Core order processing, SQL view, price-history trigger, unit/integration tests, least-privilege database grants, and a separate-database backup/restore exercise have been implemented and tested during development. Comprehensive validation, some customer/order CRUD operations, automated concurrency coverage, and a fully verified fresh-install workflow remain outstanding. This is not a production-ready webshop.

## Technology stack

- Java 21; Spring Boot 4.1.1; Spring Web MVC
- Spring Data JPA / Hibernate
- MariaDB 11.4 running in Docker
- Maven Wrapper; Git / GitHub

## Features at a glance

- Product and category CRUD, plus category product counts.
- Customer listing, lookup, and creation.
- Order listing, lookup, and **transactional creation** with multiple order items.
- Quantity and duplicate-product validation, stock availability checks, and pessimistic product-row locks during order creation.
- Product catalog view (`product_catalog_view`) exposed through read-only REST endpoints.
- Price-search index (`idx_products_price`) with `EXPLAIN` comparison.
- Price-history table and trigger that records changes to `products.price`, with a read-only history endpoint.
- API error handling for invalid arguments (`400`) and foreign-key/integrity conflicts (`409`) in the established service paths.
- Automated JUnit/Mockito tests and real-MariaDB integration tests, including a service-managed transaction rollback test.
- Restricted runtime database grants, plus a demonstrated backup and restore into a separate database.

## Prerequisites

- JDK 21
- Git
- Docker with Docker Compose (Docker Desktop with WSL2 integration is suitable on Windows)
- The course-provided `schema_populated_dump.sql`

The examples assume MariaDB is published on **host port 3307** and Spring Boot on **8080**.

## Database setup

The application uses the course-provided schema. Hibernate validates mappings rather than generating tables.

### 1. Configure Docker Compose and import the provided schema

The repository includes **`compose.yaml` in the project root**. It configures MariaDB 11.4, exposes container port `3306` on host `127.0.0.1:3307`, and uses a named volume to persist database files. Its relevant configuration is:

```yaml
environment:
  MARIADB_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
  MARIADB_DATABASE: webstore
  MARIADB_USER: webstore_app
  MARIADB_PASSWORD: ${DB_PASSWORD}
volumes:
  - webstore-data:/var/lib/mysql
  - ./database/schema_populated_dump.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
```

The original **course-provided, populated schema dump** is a local prerequisite, **not part of this repository**. Place it at `database/schema_populated_dump.sql`, relative to the root-level `compose.yaml`. Review its `CREATE DATABASE`, `USE`, and destructive statements before importing it. Keep this data dump out of Git; `database/01_database_features.sql` is the separate version-controlled project script.

Create **`.env` in the project root**, alongside `compose.yaml`:

```dotenv
DB_ROOT_PASSWORD=replace_with_root_password
DB_USERNAME=webstore_app
DB_PASSWORD=replace_with_app_password
```

Use appropriate local passwords; **do not commit `.env`**. Docker Compose automatically reads this `.env` for `${DB_ROOT_PASSWORD}` and `${DB_PASSWORD}` substitutions. `DB_USERNAME` is provided for the Spring Boot process but is not referenced by the Compose service as currently configured. Ensure `.gitignore` excludes `.env`, `database/schema_populated_dump.sql`, and temporary SQL exports. Do not confuse `.env` with `.env.local`, which Docker Compose does not automatically load by default.

From the project root, check the Compose syntax first:

```bash
docker compose config --quiet
```

**Fresh setup only:** On an installation without an existing MariaDB container/volume, use:

```bash
docker compose up -d
docker compose ps
```

If migrating from the earlier **WSL2 Compose project**, do **not** immediately run `docker compose up` from the newly relocated project: Compose normally prefixes named volumes with the Compose project name. The new directory can therefore refer to a different `webstore-data` volume, while `container_name: webstore-mariadb` can conflict with the existing container. First inspect the existing container and volume, and choose an intentional migration/reuse approach. Never run `docker compose down -v` or delete a populated volume merely to force the initialization scripts to rerun.

Once MariaDB is running, inspect the imported schema:

```bash
docker exec -it webstore-mariadb mariadb -u webstore_app -p webstore
```

```sql
SHOW TABLES;
DESCRIBE products;
SELECT id, name, price, stock_quantity FROM products LIMIT 5;
```

**Initialization behavior:** The dump mounted under `/docker-entrypoint-initdb.d/` executes only when MariaDB initializes an **empty** data directory. It does not run again after container restarts. Changing `.env` also does **not** reset database-user passwords inside an already initialized volume. The current Compose file mounts the baseline dump **only**; execute `database/01_database_features.sql` separately in the next step.

### 2. Apply project-specific database features

From the Spring Boot project root, run the version-controlled SQL script **after** the baseline schema has been imported and **before** starting Spring Boot (the JPA entities map to the view and history table):

**Recommended (works from Git Bash or WSL2):** Copy the script into the container, then execute it in an interactive MariaDB session as an administrator:

```bash
docker cp database/01_database_features.sql webstore-mariadb:/tmp/01_database_features.sql
docker exec -it webstore-mariadb mariadb -u root -p webstore
```

At the `MariaDB [webstore]>` prompt, run:

```sql
SOURCE /tmp/01_database_features.sql;
```

The script uses MariaDB command-client `DELIMITER` directives for trigger creation. If executed by root, the newly created view and trigger may have root as their `DEFINER`; the object definer must exist and have the appropriate privileges on a different installation. Do not assume a schema-only script recreates user grants. The `IF NOT EXISTS` / `OR REPLACE` statements support repeated setup runs, but this is **not** a full migration/versioning solution. Review schema changes before reapplying in shared environments. Don't commit passwords.

Verify the database objects:

```sql
SHOW FULL TABLES WHERE Table_type = 'VIEW';
SHOW INDEX FROM products WHERE Key_name = 'idx_products_price';
SHOW TRIGGERS LIKE 'products';
SELECT * FROM product_catalog_view LIMIT 5;
```

The history table starts empty on a fresh database and fills when prices change. **Historical entries from local testing are data, not supplied by the setup script.**

### 3. Configure and run Spring Boot

`src/main/resources/application.properties`:

```properties
spring.application.name=webstore
server.port=8080
spring.datasource.url=jdbc:mariadb://localhost:3307/webstore
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Docker Compose reads `.env`, but **Spring Boot launched directly through Maven or IntelliJ does not automatically load that file**. Provide the same application credentials as process environment variables (or configure them in your IntelliJ run configuration). From **Git Bash** (exports do not persist into new sessions):

```bash
export DB_USERNAME="webstore_app"
read -rsp "Database password: " DB_PASSWORD; echo
export DB_PASSWORD
./mvnw spring-boot:run
```

The host JDBC port `3307` maps to the container's MariaDB port `3306`. If your setup differs, adjust the JDBC URL. Never publish real credentials, `.env` files, or sensitive logs.

## REST API reference

Base URL: `http://localhost:8080`. All JSON examples use illustrative IDs except where explicitly described as local test observations. Actual IDs and prices depend on the imported data and subsequent changes.

### Product categories

| Method | Endpoint | Purpose | Expected success |
|---|---|---|---|
| GET | `/api/categories` | List categories | `200 OK` |
| GET | `/api/categories/{id}` | Retrieve category | `200 OK`; `404` if absent |
| POST | `/api/categories` | Create category | `201 Created` |
| PUT | `/api/categories/{id}` | Update category | `200 OK` |
| DELETE | `/api/categories/{id}` | Delete category if unreferenced | `204 No Content` |
| GET | `/api/categories/{id}/product-count` | Count products associated with category | `200 OK` |

POST/PUT request:

```json
{"name":"Test Category","description":"Created through the API"}
```

Illustrative category response:

```json
{"id":12,"name":"Test Category","description":"Created through the API"}
```

The category product-count endpoint traverses a lazy `@OneToMany` association in a read-only transaction. The exact JSON representation of the count endpoint should be checked against the current controller before final submission.

### Products

| Method | Endpoint | Purpose | Expected success |
|---|---|---|---|
| GET | `/api/products` | List products | `200 OK` |
| GET | `/api/products/{id}` | Retrieve product | `200 OK`; `404` if absent |
| POST | `/api/products` | Create product | `201 Created` |
| PUT | `/api/products/{id}` | Update product | `200 OK` |
| DELETE | `/api/products/{id}` | Delete product if unreferenced | `204 No Content` |
| GET | `/api/products/catalog` | List combined product/category records from SQL view | `200 OK` |
| GET | `/api/products/catalog/{id}` | Retrieve product from SQL view | `200 OK`; `404` if absent |
| GET | `/api/products/{id}/price-history` | List recorded price changes, newest first | `200 OK` |

POST/PUT request:

```json
{
  "name": "Test Wireless Keyboard",
  "description": "Bluetooth keyboard",
  "price": 49.99,
  "stockQuantity": 20,
  "categoryId": 1,
  "supplierId": null
}
```

Illustrative product response:

```json
{
  "id": 1001,
  "name": "Test Wireless Keyboard",
  "description": "Bluetooth keyboard",
  "price": 49.99,
  "stockQuantity": 20,
  "categoryId": 1,
  "categoryName": "Elektroniikka",
  "supplierId": null
}
```

The service validates a supplied category ID, while `supplierId` is currently a scalar field without an explicit service-level existence check. Invalid foreign-key references may therefore be rejected by MariaDB. For safe demonstration requests, `supplierId: null` is suitable. Product deletion can be blocked by order-item or history foreign keys (`409 Conflict`).

Catalog view example:

```json
{
  "productId": 1,
  "productName": "Super Bug 360",
  "price": 30.00,
  "stockQuantity": 134,
  "categoryId": 2,
  "categoryName": "Kodin tarvikkeet"
}
```

Price-history example from local testing (`GET /api/products/1/price-history`):

```json
[
  {"id":2,"productId":1,"oldPrice":25.00,"newPrice":30.00,"changedAt":"2026-10-11T17:33:53"},
  {"id":1,"productId":1,"oldPrice":22.22,"newPrice":25.00,"changedAt":"2026-10-11T16:55:44"}
]
```

An existing product with no recorded changes returns an empty JSON array. A nonexistent product currently goes through the service's `IllegalArgumentException` handling (`400`), rather than a dedicated `404` mapping; improving this is future work.

### Customers

| Method | Endpoint | Purpose | Expected success |
|---|---|---|---|
| GET | `/api/customers` | List customers | `200 OK` |
| GET | `/api/customers/{id}` | Retrieve customer | `200 OK`; `404` if absent |
| POST | `/api/customers` | Create customer | `201 Created` |

POST request:

```json
{"firstName":"Test","lastName":"Customer","email":"test.customer@example.com","phone":null}
```

Illustrative response:

```json
{"id":100001,"firstName":"Test","lastName":"Customer","email":"test.customer@example.com","phone":null}
```

Customer update and delete operations are **not yet implemented**.

### Orders

| Method | Endpoint | Purpose | Expected success |
|---|---|---|---|
| GET | `/api/orders` | List orders, including items and calculated total | `200 OK` |
| GET | `/api/orders/{id}` | Retrieve an order and its items | `200 OK`; `404` if absent |
| POST | `/api/orders` | Create multi-item order and reduce stock atomically | `201 Created` |

POST request (replace IDs with existing customer and products):

```json
{
  "customerId": 100001,
  "shippingAddressId": null,
  "items": [
    {"productId": 1, "quantity": 2},
    {"productId": 2, "quantity": 1}
  ]
}
```

Response shape (values illustrative, not a promised database result):

```json
{
  "id": 200001,
  "customerId": 100001,
  "orderDate": "2026-10-11T18:30:00",
  "deliveryDate": null,
  "shippingAddressId": null,
  "status": "NEW",
  "items": [
    {"productId":1,"productName":"Super Bug 360","quantity":2,"unitPrice":30.00,"subtotal":60.00},
    {"productId":2,"productName":"Happy Pack 382","quantity":1,"unitPrice":546.08,"subtotal":546.08}
  ],
  "totalAmount": 606.08
}
```

Order creation behavior:

1. Validate customer ID, nonempty items, positive quantities, and no duplicate product IDs.
2. Sort product IDs and acquire `PESSIMISTIC_WRITE` locks when loading products for stock updates.
3. Reject an item when requested quantity exceeds available stock.
4. Copy the current product price into each order item's `unit_price` (historical order pricing).
5. Persist the order and its order items (`CascadeType.PERSIST`) and reduce stock in one `@Transactional` operation.
6. Calculate each subtotal and order total using `BigDecimal`.

At present, `shippingAddressId` must be `null`; non-null shipping address requests are rejected until address validation is implemented. Order status changes, cancellation, and delete operations are not yet implemented. Stock-lock behavior depends on all writers following compatible transactional access patterns.

### Error behavior and limitations

| Case | Current behavior |
|---|---|
| GET a missing product/category/customer/order by ID | `404 Not Found` (where implemented) |
| Unknown category/customer in create flow | `400 Bad Request` with error message |
| Invalid order quantities, duplicate product IDs, insufficient stock | `400 Bad Request` |
| Database foreign-key/integrity conflict | `409 Conflict` through the global handler |
| Missing product in price-history service | Currently `400 Bad Request` |

Validation is not yet comprehensive: invalid or blank names, negative prices/stock, malformed inputs, and all database constraint failures have not been exhaustively tested. The system has no authentication/authorization layer.

### Manual API examples

```bash
# Browse products/categories and retrieve view-backed catalog
curl -i http://localhost:8080/api/categories
curl -i http://localhost:8080/api/categories/2/product-count
curl -i http://localhost:8080/api/products/1
curl -i http://localhost:8080/api/products/catalog/1

# Price history (records appear after a product's price is changed)
curl -i http://localhost:8080/api/products/1/price-history

# Browse customers/orders
curl -i http://localhost:8080/api/customers
curl -i http://localhost:8080/api/orders/1

# Create customer
curl -i -X POST http://localhost:8080/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Test","lastName":"Customer","email":"test.customer@example.com","phone":null}'

# Create an order: replace CUSTOMER_ID with an existing ID
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":CUSTOMER_ID,"shippingAddressId":null,"items":[{"productId":1,"quantity":2}]}'

# Insufficient-stock rejection; use a valid customer ID
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":CUSTOMER_ID,"shippingAddressId":null,"items":[{"productId":1,"quantity":10000}]}'
```

Use disposable test records and confirm available stock before POST requests. The examples above **change the database** when they create customers or orders.

## Architecture and relational mappings

```text
HTTP Client
    → REST Controller
        → Service (business logic, validation, transactions)
            → Spring Data JPA Repository
                → Hibernate / MariaDB
```

Source packages live under `src/main/java/com/github/bhandarisachin/webstore/`:

- `controller`: product/category, customer, order, and catalog controllers.
- `service`: corresponding business logic plus price-history retrieval.
- `repository`: Spring Data JPA interfaces.
- `entity`: JPA mappings for products, categories, customers, orders, order items, catalog view, and price history.
- `dto`: request/response records for products, customers, and orders.
- `exception`: centralized error handling.

Key relationships:

- `products.category_id` → `productcategories.id` (`Product` many-to-one; reverse category one-to-many).
- `products.supplier_id` → `suppliers.id` (currently scalar `supplierId` in Java).
- `orders.customer_id` → `customers.id` (order many-to-one).
- `orderitems.order_id` → `orders.id`; `orderitems.product_id` → `products.id`.
- `orderitems` has composite primary key `(order_id, product_id)` mapped with `@EmbeddedId` and `@MapsId`.
- Order-to-order-items uses bidirectional one-to-many/many-to-one with `CascadeType.PERSIST`, avoiding cascade deletion of products.

JPA relationships use lazy fetching where appropriate. Listing every order or catalog product without pagination can become expensive, and N+1 queries may arise. Pagination and fetch-plan optimization remain future improvements.

## Database features and assessment evidence

### Indexing and `EXPLAIN`

The original `products` table had indexes on its primary key, `category_id`, and `supplier_id`. We added:

```sql
CREATE INDEX IF NOT EXISTS idx_products_price ON products(price);
```

Observed plans during local testing:

| Query | `type` | `possible_keys` | `key` | Estimated `rows` | `Extra` |
|---|---|---|---|---:|---|
| `SELECT * ... WHERE price BETWEEN 100 AND 500` (after index) | `ALL` | `idx_products_price` | `NULL` | 1000 | `Using where` |
| `SELECT id, price ... WHERE price BETWEEN 100 AND 110` | `range` | `idx_products_price` | `idx_products_price` | 12 | `Using where; Using index` |

The second query can be covered by the price index because an InnoDB secondary index contains its indexed value and the primary key. These are **different queries**, so the row estimates should not be interpreted as a controlled before/after speed measurement. Indexes have storage and write-maintenance costs and are not always chosen by the optimizer.

Reproduce the explain plans:

```sql
EXPLAIN SELECT * FROM products WHERE price BETWEEN 100 AND 500;
EXPLAIN SELECT id, price FROM products WHERE price BETWEEN 100 AND 110;
```

### View: `product_catalog_view`

The view combines products with optional categories using a `LEFT JOIN`. It exposes `product_id`, `product_name`, `price`, `stock_quantity`, `category_id`, and `category_name` to `/api/products/catalog` and `/api/products/catalog/{id}`. Products with no category still appear with a null category name. It is mapped as an immutable JPA entity for read-only access.

### Trigger and price history

`product_price_history` stores `product_id`, `old_price`, `new_price`, and `changed_at`. The `trg_product_price_history` trigger runs **AFTER UPDATE** on `products` and inserts a history record only if `OLD.price <> NEW.price`.

Local test results for Product ID 1:

- Price changed `22.22 → 25.00`: history entry inserted.
- Updating to the same price `25.00`: no new entry.
- Price changed `25.00 → 30.00`: second entry inserted.
- A transaction temporarily changed `30.00 → 35.00`, then `ROLLBACK`: price returned to `30.00`, and the two committed history records remained.

The trigger runs at database level, including updates outside Spring Boot. The audit insertion participates in the product-update transaction, so both roll back together.

### Transactions and concurrency

`OrderService.createOrder()` uses `@Transactional` for order item persistence and stock reduction. Product loading uses `PESSIMISTIC_WRITE` and sorted product IDs to reduce conflicting-write risks and deadlock likelihood.

Manual tests performed:

- Successful order creation reduced available stock.
- Oversized quantity was rejected with `400` and stock remained unchanged.
- Multi-item order failed on a later item after an earlier in-memory stock decrement; the transaction rolled back, leaving product stock unchanged.

This confirms the tested rollback scenarios, **not** exhaustive concurrency correctness. A dedicated simultaneous-order integration test remains to be added.

## Database Backup and Recovery

The Webstore project uses MariaDB 11.4 with InnoDB tables. Database backup and recovery were tested using Docker and WSL2 Ubuntu.

### Backup procedure

A logical database backup was created using `mariadb-dump` with the following options:

- `--single-transaction` for a consistent InnoDB snapshot under normal conditions.
- `--routines` and `--events` to include stored routines and scheduled events.
- `--triggers` to include database triggers.

Example command:

```bash
docker exec -it webstore-mariadb \
  mariadb-dump -u root -p \
  --single-transaction \
  --routines --events --triggers \
  --result-file=/tmp/webstore_backup.sql \
  webstore
```

Copy the backup to WSL2:

```bash
docker cp webstore-mariadb:/tmp/webstore_backup.sql \
  ~/webstore-backups/webstore_backup.sql
```

### Restoration procedure

A separate database, `webstore_restore_test`, was created to avoid modifying the original database.

After inspecting the backup for database-selection statements and explicit references to the original database, it was copied into the Docker container and restored using the MariaDB client:

```sql
SOURCE /tmp/webstore_backup.sql;
```

The selected database must be verified as `webstore_restore_test` before running this command.

### Recovery verification

The restoration was tested successfully:

| Database object | Verified result |
|---|---|
| Products | 1,000 records |
| Customers | 100,001 records |
| Orders | 200,001 records |
| Order items | 1,099,774 records |
| Price history | 2 records |
| Product catalog view | Successfully queried |
| Price-history trigger | Executed successfully |

The restored product catalog view initially failed because its `webstore_app` definer lacked permissions on the restoration database. The required permissions were granted to the definer account, and the view subsequently worked.

The restored price-history trigger was also tested by temporarily updating a product price from €30.00 to €35.00. The trigger recorded the change, and `ROLLBACK` restored the original price and history count.

This demonstrates that successful recovery requires verification of data, database objects, transactions, and the permissions used by stored database objects.

**Security:** Backup files contain database records and must not be committed to GitHub. The administrative account is used for backup and restoration, while the application account has restricted permissions on the main database.

### Database privileges and remaining security limitations

- Application credentials are supplied using `DB_USERNAME` and `DB_PASSWORD`; keep local credential files and actual passwords outside version control.
- The tested local main-database grants for `webstore_app` were restricted from `ALL PRIVILEGES` to `SELECT, INSERT, UPDATE, DELETE, TRIGGER` on `webstore.*`. Run `SHOW GRANTS FOR 'webstore_app'@'%';` as an administrator to inspect the active grants. The independent `webstore_test` database retained broader development-only privileges.
- The original catalog view uses `SQL SECURITY DEFINER`, and the price-history trigger has `webstore_app` as definer in the tested database. Price updates, trigger insertion, rollback, and a view query were successfully retested after permission restrictions. Schema scripts executed under a different administrator may create objects with a different definer.
- To reproduce the restricted local grant *after initial schema/object creation*, an administrator can apply the following, but should inspect existing grants and definers first:

```sql
REVOKE ALL PRIVILEGES ON webstore.* FROM 'webstore_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, TRIGGER ON webstore.* TO 'webstore_app'@'%';
```

- The runtime database account still has database-wide DML permissions and `TRIGGER` privilege because of the current trigger design. These are not the strictest possible production grants. No application-level authentication or authorization has been implemented.
- Docker volumes preserve database data across container restarts, but they are **not backups**. The separate restore test described above was completed. The event scheduler was enabled in the example Compose setup, but no scheduled event is claimed as implemented.
- Custom schema features are versioned in `database/01_database_features.sql`. No Flyway/Liquibase-style migration framework is configured.

## Automated tests

The test suite was developed and reported passing using the dedicated `webstore_test` MariaDB database. `src/test/resources/application-test.properties` sets the JDBC database to `webstore_test`; all Spring context/integration tests must activate the `test` profile. Mockito-based unit tests do not require a running database.

- `WebstoreApplicationTests`: Spring application context startup using `@ActiveProfiles("test")`.
- `OrderServiceTest`: invalid empty orders, insufficient stock, duplicate product IDs, and successful order creation logic with mocked repositories.
- `OrderIntegrationTest`: saving a customer, persisting an order and its items, checking stock changes, and exercising a failure path with test-managed transaction rollback.
- `OrderRollbackIntegrationTest`: checks the real database inventory and order count after an unsuccessful order service transaction. Its fixture data is committed to the **test** database and cleaned up afterward.

Integration-test setup: create an isolated `webstore_test` database, import the *schema only* from the original database, and grant the local test user suitable rights to that database. The schema-only export was generated with `mariadb-dump --no-data --skip-triggers`; this creates tables/views but does **not** populate records or install triggers. Test fixtures are inserted by test code. Do not point test-profile JDBC settings at the populated `webstore` database.

From the project root, with the Docker container running and database credentials available in the **same terminal**:

```bash
./mvnw clean test
./mvnw clean package
```

For an individual test:

```bash
./mvnw -Dtest=OrderRollbackIntegrationTest test
```

`BUILD SUCCESS` with an executed test count verifies the configured tests passed in that environment; it is not evidence of exhaustive validation, security, or concurrency coverage. Mockito may emit a Java-agent self-attachment warning that should be addressed in a future test-build configuration.

## Known limitations and next work

- Complete field validation, standardized domain-specific HTTP errors, and automated HTTP-controller tests.
- Add customer update/delete and order status/cancellation endpoints **if required by the instructor**; the current API does not include them.
- Add pagination and search/filter APIs and optimize N+1/fetch performance for large order listings.
- Add simultaneous-order concurrency tests and improve fixture cleanup resilience in case of database failures.
- Confirm a complete fresh-install procedure, including schema import, custom SQL features, definer permissions, application startup, and end-to-end endpoint tests. Test-database schema export and manual restore tests were completed, but this exact complete sequence has not yet been verified.
- Confirm endpoint request/response bodies against the final checked-out Java code. Do not assume example IDs exist in every database.

## Build and run

With MariaDB running, project-specific SQL objects created, and credentials configured:

```bash
./mvnw compile
./mvnw clean test
./mvnw clean package
./mvnw spring-boot:run
```

`clean package` executes configured Maven tests unless skipped. The `test` profile is set on the Spring test classes; normal app startup continues to use the `webstore` database.

## Development notes

- The API is a coursework prototype, not a security-hardened service.
- Never commit `.env`, credentials, the course-populated `database/schema_populated_dump.sql`, temporary database exports, or generated build artifacts. Keep `compose.yaml` and `database/01_database_features.sql` in version control.
- Retain the teacher-provided SQL schema as the baseline and keep custom database changes separate.
- Use small, descriptive Git commits and update this README when behavior changes.
