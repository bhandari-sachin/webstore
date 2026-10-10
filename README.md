# Webstore Management REST API

A Java Spring Boot backend for the **Database Solutions** course. The project uses the course-provided relational Webstore schema in MariaDB and is being developed incrementally into a small webshop/order-management service.

> **Project status:** Work in progress. Product and product-category CRUD are implemented and have been manually tested. Customer and order management and the database-focused course features are still to be developed or verified.

## Technology stack

- Java 21
- Spring Boot 4.1.1 / Spring Web MVC
- Spring Data JPA (Hibernate)
- MariaDB 11.4 (Docker)
- Maven Wrapper
- Git / GitHub

## Current functionality

- CRUD endpoints for product categories and products.
- Controller → Service → Repository layering with constructor dependency injection.
- JPA mapping of `productcategories` and `products`, including a `Product` → `ProductCategory` many-to-one association (lazy loaded).
- Request/response DTOs for products; the product response includes category ID and name.
- Lookup by ID with `404 Not Found` when no record exists.
- Product creation checks that a supplied category ID exists.
- Centralized handling of invalid arguments (`400 Bad Request`) and database-integrity violations (`409 Conflict`).
- Product deletion respects foreign-key constraints, including references from `orderitems`.

**Known limitations:** Product input does not yet have complete field validation, supplier IDs are not yet validated in the service, the category API currently exposes JPA entities directly, and product/category delete conflicts have only generic error responses. These are not production-ready APIs and do not include authentication.

## Prerequisites

- JDK 21
- Git
- Docker with Docker Compose (Docker Desktop with WSL2 integration is suitable on Windows)
- A copy of the course-provided `schema_populated_dump.sql`

The Spring Boot project runs on Windows or WSL; MariaDB runs in Docker. The example configuration below assumes the database is published to `127.0.0.1:3307` on the host. Change the port if your actual Compose configuration differs.

## Database setup

The project uses the **course-provided schema** rather than generating new tables automatically.

1. Place `schema_populated_dump.sql` alongside your `compose.yaml` in the database setup directory. Review the dump before importing: check for `CREATE DATABASE`, `USE`, and destructive SQL statements.
2. Configure the Compose service to use MariaDB 11.4, the `webstore` database, a named volume, and an initialization mount such as:

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

3. In that same directory, create an **untracked** `.env` file defining `DB_ROOT_PASSWORD` and `DB_PASSWORD`, with passwords of your choosing. Add `.env` to `.gitignore`.
4. Start the database:

   ```bash
   docker compose up -d
   docker compose ps
   ```

5. Inspect the imported data:

   ```bash
   docker exec -it webstore-mariadb mariadb -u webstore_app -p webstore
   ```

   ```sql
   SHOW TABLES;
   SELECT * FROM products LIMIT 5;
   DESCRIBE products;
   ```

**Note:** SQL scripts in `/docker-entrypoint-initdb.d/` run only when MariaDB initializes an **empty data directory**. Restarting a container with an existing volume does not re-import the dump. Do not delete an existing database volume simply to re-run initialization without first safeguarding its data.

## Spring Boot configuration

In `src/main/resources/application.properties`:

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

`ddl-auto=validate` checks JPA mappings against the existing database schema without modifying it.

From **Git Bash on Windows**, set credentials for the current terminal session and start the application:

```bash
export DB_USERNAME="webstore_app"
export DB_PASSWORD="<your-app-password>"
./mvnw spring-boot:run
```

The URL uses host port **3307**, corresponding to the Docker Compose mapping `3307:3306`. If your host-side port is **3306**, adjust the URL accordingly. Avoid putting real credentials in tracked configuration or screenshots/logs.

The API is served at `http://localhost:8080` unless you change `server.port`.

## REST API reference (implemented)

### Product categories

| Method | Endpoint | Purpose | Successful response |
|---|---|---|---|
| GET | `/api/categories` | List all categories | `200 OK` — JSON array |
| GET | `/api/categories/{id}` | Get one category | `200 OK` — JSON object |
| POST | `/api/categories` | Create a category | `201 Created` — saved JSON object |
| PUT | `/api/categories/{id}` | Update a category | `200 OK` — updated JSON object |
| DELETE | `/api/categories/{id}` | Delete a category | `204 No Content` |

Example category request body for **POST** or **PUT**:

```json
{
  "name": "Test Category",
  "description": "Created through the API"
}
```

Example response (illustrative; generated ID depends on the database):

```json
{
  "id": 12,
  "name": "Test Category",
  "description": "Created through the API"
}
```

Missing IDs return `404 Not Found`. A deletion prevented by a foreign-key constraint returns a generic `409 Conflict` after the database-integrity handler is applied. Category input validation is not yet comprehensive.

### Products

| Method | Endpoint | Purpose | Successful response |
|---|---|---|---|
| GET | `/api/products` | List all products | `200 OK` — JSON array |
| GET | `/api/products/{id}` | Get one product | `200 OK` — JSON object |
| POST | `/api/products` | Create a product | `201 Created` — saved JSON object |
| PUT | `/api/products/{id}` | Update a product | `200 OK` — updated JSON object |
| DELETE | `/api/products/{id}` | Delete a product | `204 No Content` |

Example **POST** or **PUT** request body:

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

Example response (illustrative):

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

Product requests use `ProductRequest` and responses use `ProductResponse`. The `categoryId` must refer to an existing category if it is not `null`. The service currently stores `supplierId` as a scalar without checking whether the supplier exists; for reliable initial testing, use `null` for that field.

Errors observed or explicitly handled:

| Case | Status | Response |
|---|---|---|
| Requested product/category does not exist | `404 Not Found` | Empty response body |
| Product references an unknown category on create/update | `400 Bad Request` | JSON error message |
| Delete/update violates a database relationship | `409 Conflict` | Generic JSON error message |

For PUT, supply the complete editable product representation. A `null` category ID clears the category association.

### Test using Git Bash

```bash
# List categories
curl -i http://localhost:8080/api/categories

# Get product by ID
curl -i http://localhost:8080/api/products/1

# Create product; choose an existing category ID
curl -i -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Wireless Keyboard","description":"Test product","price":49.99,"stockQuantity":20,"categoryId":1,"supplierId":null}'

# Update product; replace ID with your created test product ID
curl -i -X PUT http://localhost:8080/api/products/1001 \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated Keyboard","description":"Updated test product","price":39.99,"stockQuantity":15,"categoryId":1,"supplierId":null}'

# Delete only a safe test product with no order-item references
curl -i -X DELETE http://localhost:8080/api/products/1001
```

IDs `1` and `1001` are **examples**, not guaranteed records. Only delete your own disposable test data.

## Architecture

```text
HTTP client
  → REST Controller
    → Service (business logic / transactions)
      → Spring Data JPA Repository
        → Hibernate / MariaDB
```

Main code packages:

```text
src/main/java/com/github/bhandarisachin/webstore/
├── controller/
│   ├── ProductCategoryController.java
│   └── ProductController.java
├── dto/
│   ├── ProductRequest.java
│   └── ProductResponse.java
├── entity/
│   ├── ProductCategory.java
│   └── Product.java
├── exception/
│   └── GlobalExceptionHandler.java
├── repository/
│   ├── ProductCategoryRepository.java
│   └── ProductRepository.java
├── service/
│   ├── ProductCategoryService.java
│   └── ProductService.java
└── WebstoreApplication.java
```

This tree describes the files implemented during development; confirm local filenames/locations before final submission.

## Database design and integrity

The current API mainly uses these tables from the provided schema:

- `productcategories`: `id`, `name`, `description`.
- `products`: `id`, `name`, `description`, `price`, `stock_quantity`, `category_id`, `supplier_id`.
- `suppliers`: referenced by `products.supplier_id`, but no Supplier API is implemented yet.
- `orderitems`: references products and can prevent deletion of products involved in orders.

Key relationships confirmed from `SHOW CREATE TABLE products`:

```text
products.category_id → productcategories.id
products.supplier_id → suppliers.id
orderitems.product_id → products.id  (confirmed by deletion constraint error)
```

MariaDB enforces referential integrity. A deletion of an order-referenced product produced a foreign-key violation; the API now maps integrity violations to `409 Conflict` instead of exposing a `500 Internal Server Error`.

`Product` currently uses `@ManyToOne(fetch = LAZY)` for category. Bidirectional `@OneToMany` mapping, cascading tests, and fetch-performance comparisons are upcoming work.

## Database features and assessment evidence

The course requires appropriate application and documentation of database techniques. The following reflects **current implementation status**, not a claim that the entire course project is finished.

| Feature | Status / next evidence |
|---|---|
| Primary/foreign keys | Existing schema used; product/category and product/order-item constraints observed |
| Existing schema indexes | Product foreign-key indexes observed; deliberate search-index design and performance measurements pending |
| Views | Inspect existing dump objects; application-specific usage/documentation pending |
| Triggers | Inspect existing dump objects; implementation and demonstration pending |
| Transactions | `@Transactional` used in relevant service operations; multi-table order creation and rollback demonstration pending |
| Temporal features | Product price-history solution and query/API demonstration pending |
| Events | Database event scheduler enabled in Compose; actual scheduled-event implementation/evidence pending |
| Database security | Separate app database user configured; least-privilege review pending |
| Backup/recovery | Backup and restore procedure/testing pending |
| Schema changes | Migration/change-management approach pending |
| API tests | Manual cURL checks performed; automated tests and reproducible test evidence pending |

## Planned functionality

To meet the original requirement of a **small order management system / webshop backend**, the next implementation stages are:

1. Finish and verify the category/product JPA relationship, including suitable fetching/cascading decisions.
2. Add essential customer and order endpoints, including order items.
3. Implement transactional order creation with inventory checks and stock updates.
4. Add practical product search/filtering and evaluate query performance with indexes and `EXPLAIN`.
5. Expose a useful SQL view and implement/document price-change history with triggers or other temporal techniques.
6. Verify database permissions, event usage if justified, backup/restore, and schema-change practices.
7. Complete automated tests, manual API examples, and final README evidence.

The instructor's project description evaluates API scope, versatile database-feature use, and documentation quality. Specific additional mappings (such as inheritance or M:N) will be prioritized only if required by the separate course assignments or useful for this implementation.

## Build and test

With the MariaDB container running and credentials configured in the terminal:

```bash
./mvnw clean package
./mvnw spring-boot:run
```

Automated tests may require a configured test database/application context. Do not treat manual endpoint testing as a substitute for passing the Maven test suite.

## Development notes

- The REST API is a course project, not a production-deployed or security-hardened service.
- Never commit `.env`, plaintext passwords, or generated build artifacts.
- Keep the teacher's original SQL dump as the baseline; document any schema changes separately.
- Commit features in small, working increments with descriptive Git messages.
