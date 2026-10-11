# Webstore Management REST API

A Java Spring Boot backend for the **Database Solutions** course, built around the course-provided relational `webstore` schema in MariaDB. The project implements product and category management, customer lookup/creation, transactional order placement, catalog reporting, and product price-change history.

> **Status:** Functional coursework prototype. The endpoints and database features described below have been developed and manually exercised during the project. Automated integration tests, complete input validation, least-privilege database permissions, and backup/restore verification remain outstanding. Do not treat this as a production-ready webshop.

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

## Prerequisites

- JDK 21
- Git
- Docker with Docker Compose (Docker Desktop with WSL2 integration is suitable on Windows)
- The course-provided `schema_populated_dump.sql`

The examples assume MariaDB is published on **host port 3307** and Spring Boot on **8080**.

## Database setup

The application uses the course-provided schema. Hibernate validates mappings rather than generating tables.

### 1. Start MariaDB and import the provided schema

Place `schema_populated_dump.sql` alongside your database `compose.yaml`. Review the dump for `CREATE DATABASE`, `USE`, or destructive SQL before importing it. An example Compose configuration follows:

```yaml
services:
  database:
    image: mariadb:11.4
    container_name: webstore-mariadb
    restart: unless-stopped
    environment:
      MARIADB_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MARIADB_DATABASE: webstore
      MARIADB_USER: webstore_app
      MARIADB_PASSWORD: ${DB_PASSWORD}
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - webstore-data:/var/lib/mysql
      - ./schema_populated_dump.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
    command:
      - --event-scheduler=ON
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 30s
volumes:
  webstore-data:
```

Create an **untracked** `.env` file in the Compose directory containing `DB_ROOT_PASSWORD` and `DB_PASSWORD`. Ensure `.env` is ignored by Git. Then run:

```bash
docker compose up -d
docker compose ps
```

Connect to inspect the imported schema:

```bash
docker exec -it webstore-mariadb mariadb -u webstore_app -p webstore
```

```sql
SHOW TABLES;
DESCRIBE products;
SELECT id, name, price, stock_quantity FROM products LIMIT 5;
```

**Important:** Scripts mounted in `/docker-entrypoint-initdb.d/` run only when MariaDB initializes an **empty** data directory. Restarting a container with its existing volume does not re-import the schema. Never remove a volume just to rerun initialization without safeguarding the data.

### 2. Apply project-specific database features

From the Spring Boot project root, run the version-controlled SQL script **after** the baseline schema has been imported and **before** starting Spring Boot (the JPA entities map to the view and history table):

```bash
docker exec -i webstore-mariadb mariadb -u root -p webstore < database/01_database_features.sql
```

**Shell caveat:** With `docker exec -i` consuming standard input from the SQL file, an interactive password prompt may not work as expected. A reliable alternative is to first copy the script into the container, then execute it in an interactive MariaDB session:

```bash
docker cp database/01_database_features.sql webstore-mariadb:/tmp/01_database_features.sql
docker exec -it webstore-mariadb mariadb -u root -p webstore
```

At the `MariaDB [webstore]>` prompt, run:

```sql
SOURCE /tmp/01_database_features.sql;
```

The script uses MariaDB command-client `DELIMITER` directives for trigger creation. The `IF NOT EXISTS` / `OR REPLACE` statements support repeated setup runs, but this is **not** a full migration/versioning solution. Review schema changes before reapplying in shared environments. Don't commit passwords.

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

From **Git Bash**:

```bash
export DB_USERNAME="webstore_app"
export DB_PASSWORD="<your-app-password>"
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

### Security and recoverability

- Credentials are provided via environment variables rather than committed properties.
- An application database user is configured, but actual privileges still need review for least privilege. The current prototype does not implement application-level authentication.
- A Docker named volume holds database data; a named volume is **not a backup**. A tested export/restore procedure is still needed.
- The MariaDB event scheduler is enabled in the example Compose setup, but no scheduled database event has been demonstrated. Do not cite that as implemented event functionality.
- The additional schema features are versioned in `database/01_database_features.sql`; a formal migration tool (such as Flyway or Liquibase) has not been configured.

## Known limitations and next work

- Add comprehensive validation, domain-specific 404/409 handling, and automated API/integration tests.
- Implement any remaining customer/order CRUD operations actually required by the instructor (currently customer update/delete and order updates/cancellations are absent).
- Add pagination, useful search/filter endpoints, and improve N+1/fetch performance.
- Verify least-privilege database access and backup/restore on a disposable database.
- Complete schema-change/migration practices and final deployment/reproducibility verification.
- Confirm all endpoint request/response formats against the final Java code before submission.

## Build and test

With MariaDB running, project-specific SQL objects created, and credentials configured:

```bash
./mvnw compile
./mvnw clean package
./mvnw spring-boot:run
```

`clean package` runs configured Maven tests; a successful compile alone is not evidence that integration tests pass. Manual API checks and database rollback tests described above are supplementary evidence.

## Development notes

- The API is a coursework prototype, not a security-hardened service.
- Never commit `.env`, credentials, database dumps containing confidential data, or generated build artifacts.
- Retain the teacher-provided SQL schema as the baseline and keep custom database changes separate.
- Use small, descriptive Git commits and update this README when behavior changes.
