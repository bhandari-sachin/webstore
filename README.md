# Webstore Management REST API

Spring Boot REST API for managing a webshop using the MariaDB schema supplied in the Database Solutions course. The application supports product and category management, customer registration, transactional order placement, a catalog view, and price-change history.

## Technology

- Java 21, Spring Boot 4.1.1, Spring Web MVC
- Spring Data JPA / Hibernate; Maven Wrapper
- MariaDB 11.4; Docker Compose

## Scope

- Product and category CRUD; product counts per category
- Customer list, lookup, and creation
- Order list, lookup, and atomic creation with multiple items, stock checks, and pessimistic row locking
- SQL catalog view, price index, and price-history trigger exposed through read-only endpoints
- JUnit/Mockito unit tests and MariaDB integration tests for order creation and rollback
- Database privilege restrictions and verified logical backup/restore procedure

## Setup

### Prerequisites

JDK 21, Docker with Docker Compose, and the course-supplied `schema_populated_dump.sql`. Examples use MariaDB on `localhost:3307` and the API on `localhost:8080`.

### 1. Prepare the database files

The repository includes `compose.yaml` and `database/01_database_features.sql`. Place the course-provided populated dump at `database/schema_populated_dump.sql`. The dump is a local setup dependency and is excluded from version control. Its database selection and initialization statements must be compatible with the `webstore` database specified in Compose.

```text
webstore/
├── compose.yaml
├── .env                         # local, git-ignored
├── database/
│   ├── schema_populated_dump.sql # course-provided, git-ignored
│   └── 01_database_features.sql  # version-controlled
├── src/
├── pom.xml
└── README.md
```

### 2. Configure environment variables

Create `.env` alongside `compose.yaml`:

```dotenv
DB_ROOT_PASSWORD=<root_password>
DB_USERNAME=webstore_app
DB_PASSWORD=<application_password>
```

Docker Compose uses `DB_ROOT_PASSWORD` and `DB_PASSWORD` to initialize MariaDB. Spring Boot uses `DB_USERNAME` and `DB_PASSWORD` for its JDBC connection. `.env` is excluded from version control; Spring Boot started directly from IntelliJ or Maven requires these variables in its process environment.

### 3. Initialize MariaDB

From the repository root:

```bash
docker compose config --quiet
docker compose up -d
docker compose ps
```

The Compose configuration imports `database/schema_populated_dump.sql` via `/docker-entrypoint-initdb.d/01-schema.sql` **only for a new, empty database volume**. Existing database volumes are not reinitialized on restart; changing `.env` does not update passwords already stored in an initialized database.

The original development container was started from a different WSL2 Compose directory. Compose project names determine default volume names, so launching this file from the repository root may select a different database volume or conflict with the existing `webstore-mariadb` container. Existing installations require volume/container inspection before migration. The fresh-install sequence has not yet been verified end to end.

Inspect the imported tables:

```bash
docker exec -it webstore-mariadb mariadb -u webstore_app -p webstore
```

```sql
SHOW TABLES;
DESCRIBE products;
SELECT id, name, price, stock_quantity FROM products LIMIT 5;
```

### 4. Install project database objects

The application uses `spring.jpa.hibernate.ddl-auto=validate`; JPA does not create the custom objects. Run the project SQL script after importing the baseline schema:

```bash
docker cp database/01_database_features.sql webstore-mariadb:/tmp/01_database_features.sql
docker exec -it webstore-mariadb mariadb -u root -p webstore
```

In the MariaDB client:

```sql
SOURCE /tmp/01_database_features.sql;
SHOW INDEX FROM products WHERE Key_name = 'idx_products_price';
SHOW FULL TABLES WHERE Table_type = 'VIEW';
SHOW TRIGGERS LIKE 'products';
SELECT * FROM product_catalog_view LIMIT 5;
```

The script creates `idx_products_price`, `product_catalog_view`, `product_price_history`, and `trg_product_price_history`. It uses MariaDB `DELIMITER` statements, so it is executed through the MariaDB command-line client. Views and triggers depend on their SQL `DEFINER` account and its privileges. Object creation and privilege configuration are separate setup operations; no migration framework is configured.

### 5. Run the application

`src/main/resources/application.properties` specifies the connection to `jdbc:mariadb://localhost:3307/webstore` and resolves credentials through `DB_USERNAME` and `DB_PASSWORD`.

For Git Bash:

```bash
export DB_USERNAME=webstore_app
read -rsp "Database password: " DB_PASSWORD; echo
export DB_PASSWORD
./mvnw spring-boot:run
```

For IntelliJ, configure `DB_USERNAME` and `DB_PASSWORD` in the application's run configuration. The API base URL is `http://localhost:8080`.

## REST API reference

Base URL: `http://localhost:8080`. Request and response examples illustrate the API format; IDs and values vary with database contents.

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

Example response:

```json
{"id":12,"name":"Test Category","description":"Created through the API"}
```

The category product count is obtained from a lazy `@OneToMany` association within a read-only transaction.

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

Example response:

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

Category references are validated by the service; `supplierId` is mapped as a scalar without a service-level existence check. Invalid foreign-key references may therefore be rejected by MariaDB. `supplierId` may be `null`. Product deletion can be blocked by order-item or history foreign keys (`409 Conflict`).

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

Example recorded price history (`GET /api/products/1/price-history`):

```json
[
  {"id":2,"productId":1,"oldPrice":25.00,"newPrice":30.00,"changedAt":"2026-10-11T17:33:53"},
  {"id":1,"productId":1,"oldPrice":22.22,"newPrice":25.00,"changedAt":"2026-10-11T16:55:44"}
]
```

An existing product with no recorded changes returns an empty JSON array. A missing product ID currently returns `400 Bad Request` through the service exception handler.

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

Example response:

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

Example response:

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

At present, `shippingAddressId` must be `null`; non-null shipping address requests are rejected because shipping-address validation is not implemented. Order status changes, cancellation, and delete operations are not yet implemented. Pessimistic locking protects stock updates performed through the order service.

### Error behavior and limitations

| Case | Current behavior |
|---|---|
| GET a missing product/category/customer/order by ID | `404 Not Found` (where implemented) |
| Unknown category/customer in create flow | `400 Bad Request` with error message |
| Invalid order quantities, duplicate product IDs, insufficient stock | `400 Bad Request` |
| Database foreign-key/integrity conflict | `409 Conflict` through the global handler |
| Missing product in price-history service | Currently `400 Bad Request` |

Input validation is partial, and the API has no authentication or authorization layer. See [Limitations](#limitations).

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

# Set CUSTOMER_ID to an existing customer ID before creating an order
# Example: CUSTOMER_ID=100001
CUSTOMER_ID=100001
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":${CUSTOMER_ID},\"shippingAddressId\":null,\"items\":[{\"productId\":1,\"quantity\":2}]}"

# Insufficient-stock rejection
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\":${CUSTOMER_ID},\"shippingAddressId\":null,\"items\":[{\"productId\":1,\"quantity\":10000}]}"
```

POST examples persist records and may modify product stock.

## Architecture and relational mappings

```text
HTTP Client
    → REST Controller
        → Service (business logic, validation, transactions)
            → Spring Data JPA Repository
                → Hibernate / MariaDB
```

Source packages are organized under `src/main/java/com/github/bhandarisachin/webstore/`:

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

Relationships use lazy loading where appropriate. Collection endpoints do not implement pagination; some association access may produce N+1 queries.

## Database implementation

### Indexing and `EXPLAIN`

The `products` table has primary-key and foreign-key indexes. The project adds:

```sql
CREATE INDEX IF NOT EXISTS idx_products_price ON products(price);
```

Recorded `EXPLAIN` results:

| Query | `type` | `possible_keys` | `key` | Estimated `rows` | `Extra` |
|---|---|---|---|---:|---|
| `SELECT * ... WHERE price BETWEEN 100 AND 500` (after index) | `ALL` | `idx_products_price` | `NULL` | 1000 | `Using where` |
| `SELECT id, price ... WHERE price BETWEEN 100 AND 110` | `range` | `idx_products_price` | `idx_products_price` | 12 | `Using where; Using index` |

The second query can be covered by the price index because an InnoDB secondary index contains its indexed value and the primary key. The queries differ in projection and range, so the results do not establish a controlled before/after performance comparison. Indexes have storage and write-maintenance costs and are not always chosen by the optimizer.

Queries:

```sql
EXPLAIN SELECT * FROM products WHERE price BETWEEN 100 AND 500;
EXPLAIN SELECT id, price FROM products WHERE price BETWEEN 100 AND 110;
```

### View: `product_catalog_view`

The view combines products with optional categories using a `LEFT JOIN`. It exposes `product_id`, `product_name`, `price`, `stock_quantity`, `category_id`, and `category_name` to `/api/products/catalog` and `/api/products/catalog/{id}`. Products with no category still appear with a null category name. It is mapped as an immutable JPA entity for read-only access.

### Trigger and price history

`product_price_history` stores `product_id`, `old_price`, `new_price`, and `changed_at`. The `trg_product_price_history` trigger runs **AFTER UPDATE** on `products` and inserts a history record only if `OLD.price <> NEW.price`.

Verified behavior for product ID 1:

- Price changed `22.22 → 25.00`: history entry inserted.
- Updating to the same price `25.00`: no new entry.
- Price changed `25.00 → 30.00`: second entry inserted.
- A transaction temporarily changed `30.00 → 35.00`, then `ROLLBACK`: price returned to `30.00`, and the two committed history records remained.

The trigger runs at database level, including updates outside Spring Boot. The audit insertion participates in the product-update transaction, so both roll back together.

### Transactions and concurrency

`OrderService.createOrder()` uses `@Transactional` for order item persistence and stock reduction. Product loading uses `PESSIMISTIC_WRITE` and sorted product IDs to reduce conflicting-write risks and deadlock likelihood.

Verified order scenarios:

- Successful order creation reduced available stock.
- Oversized quantity was rejected with `400` and stock remained unchanged.
- Multi-item order failed on a later item after an earlier in-memory stock decrement; the transaction rolled back, leaving product stock unchanged.

Simultaneous competing orders have not been covered by a dedicated concurrency test.

## Backup and restoration

The populated `webstore` database was backed up with `mariadb-dump`, including triggers, routines, and events:

```bash
docker exec -it webstore-mariadb mariadb-dump -u root -p \
  --single-transaction --routines --events --triggers \
  --result-file=/tmp/webstore_backup.sql webstore
docker cp webstore-mariadb:/tmp/webstore_backup.sql \
  ~/webstore-backups/webstore_backup.sql
```

The dump was restored to an isolated database named `webstore_restore_test` by selecting that database in the MariaDB client and executing `SOURCE /tmp/webstore_backup.sql;`. The dump was inspected for database-selection statements before the restore. The database must exist and the file must first be copied into the container.

| Restored object | Verification |
|---|---|
| `products` | 1,000 rows |
| `customers` | 100,001 rows |
| `orders` | 200,001 rows |
| `orderitems` | 1,099,774 rows |
| `product_price_history` | 2 rows |
| `product_catalog_view` | Query succeeded after granting the view definer access to the restored database |
| `trg_product_price_history` | Inserted a history row on price update; both update and history insert reverted on `ROLLBACK` |

The restored view initially required permissions for its `webstore_app` definer on `webstore_restore_test`. Backup validation therefore covered data, SQL objects, definer permissions, and trigger rollback behavior. Backups are stored outside the repository.

## Database permissions

The tested development `webstore_app` account has `SELECT`, `INSERT`, `UPDATE`, `DELETE`, and `TRIGGER` privileges on `webstore.*`; the isolated test database has separate broader permissions. An administrator can inspect the effective privileges with:

```sql
SHOW GRANTS FOR 'webstore_app'@'%';
```

The main database permissions were configured after initialization using:

```sql
REVOKE ALL PRIVILEGES ON webstore.* FROM 'webstore_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, TRIGGER ON webstore.* TO 'webstore_app'@'%';
```

These grants describe the tested development configuration, not an automated step in `compose.yaml`. The existing view/trigger used `webstore_app` as their definer; fresh objects may have a different definer depending on the account that executes the SQL script. The runtime account retains database-wide DML and `TRIGGER` privileges. Application authentication and authorization are not implemented.

## Testing

Automated tests use JUnit 5, Mockito, and a separate MariaDB `webstore_test` database configured by `src/test/resources/application-test.properties` and `@ActiveProfiles("test")`.

| Test class | Coverage |
|---|---|
| `WebstoreApplicationTests` | Spring application context startup |
| `OrderServiceTest` | Empty items, duplicate products, insufficient stock, successful stock reduction (mocked dependencies) |
| `OrderIntegrationTest` | Customer persistence, order/item persistence, stock changes, test-managed rollback |
| `OrderRollbackIntegrationTest` | Service-managed rollback with committed fixtures and subsequent cleanup |

The test database was initialized using a schema-only dump (`mariadb-dump --no-data --skip-triggers`) from the existing database. The export includes table/view definitions but does not provide populated records or triggers. Test fixtures are created by the tests; integration tests require a running `webstore_test` database and appropriate grants.

```bash
./mvnw clean test
./mvnw clean package
```

The suite passed during development. No automated simultaneous-order concurrency or HTTP-controller test suite has been established. Mockito may emit a Java-agent self-attachment warning.

## Limitations

- Customer update/delete, order status update/cancellation, and shipping-address validation are not implemented.
- Field validation and HTTP error classification are incomplete; no API authentication/authorization is provided.
- Collection endpoints lack pagination, and some JPA associations may incur N+1 queries.
- Concurrent order submissions have not been tested with a dedicated simultaneous-client scenario.
- The complete fresh-install workflow from the relocated Compose project has not yet been validated.
- A schema migration framework (Flyway/Liquibase) is not configured.

## Repository hygiene

The repository tracks the application, `compose.yaml`, and `database/01_database_features.sql`. Local `.env` files, the course-provided populated schema dump, database backups, and test exports are excluded from version control.
