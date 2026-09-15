# Phase 1 Decisions — User and Product Services

This addendum resolves only the minimum decisions required to implement the first two services. The files under `specs/` remain authoritative and unchanged. Unresolved decisions for Order, Inventory, Payment, and AI-Customer services remain out of scope.

## Shared decisions

- Implementation scope: `user-service` and `product-service` only.
- Java 21 and Spring Boot 3.3.2, built as a Maven multi-module project.
- H2 is used for local development and automated tests. PostgreSQL is deferred.
- IDs are strings with service prefixes and generated random suffixes, matching the OpenAPI examples.
- JWT uses HMAC-SHA256. The secret is supplied by `JWT_SECRET`; a development-only default is allowed locally and must not be used in production.
- JWT contains `sub` (user ID), `email`, and `role`. Product Service validates the same signature and uses the `role` claim for Admin endpoints.
- JSON monetary values map to Java `BigDecimal` despite the OpenAPI `number/double` representation.
- API errors use the OpenAPI `ErrorResponse` shape: `code`, `message`, and `timestamp`.

## User Service decisions

- Email is normalized to lowercase and validated with standard email syntax.
- Password policy: 8–72 characters and at least one letter and one digit.
- Passwords use BCrypt and are never returned.
- Five failed logins inside a rolling ten-minute window lock the account for ten minutes. Successful login clears the failure window.
- Password-reset tokens expire after 15 minutes, are single-use, and are stored as SHA-256 hashes. The development implementation returns no token in the public API, consistent with the OpenAPI response.
- Account deletion is soft deletion by setting status to `DEACTIVATED`.
- Admin provisioning is not exposed publicly; registration always creates `CUSTOMER` accounts.

## Product Service decisions

- `Product` and `Category` are separate aggregate roots.
- Category creation APIs are out of scope. A default category is seeded for local/test use; product creation rejects an unknown category.
- Public browse, detail, and search endpoints return only `ACTIVE` products.
- Search is case-insensitive substring matching over name and description. Blank queries return HTTP 400.
- Product updates do not change category because `ProductUpdateRequest` does not contain `categoryId`.
- Product details attempt to query Inventory Service. If Inventory is unavailable, the product is still returned with stock `0` and `isInStock=false` for this milestone.
- Admin create/update/deactivate endpoints require a valid JWT with role `ADMIN`.

## Explicitly deferred

All Order, Inventory, Payment, Kafka, Saga, return/exchange, and AI-agent workflow decisions identified by the Phase 1 audit remain unresolved and must be confirmed before those services are implemented.
