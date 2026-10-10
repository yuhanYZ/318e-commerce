# User Stories — Product Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10
**Source:** Split from [iter1/2-user-stories.md](../iter1/2-user-stories.md) — Iteration 1, archived for history.

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| Product Service | P1 | Browse Product Catalog | Customer |
| Product Service | P2 | View Product Details | Customer |
| Product Service | P3 | Search Products | Customer |
| Product Service | P4 | Create / Update / Deactivate a Product | Admin |

---

## Product Service

### User Story P1: Browse Product Catalog
- **As a** Customer, **I want to** browse products by category.
- **Scenario:**
  i. The user requests products for a given category from Product Service.
  ii. Product Service returns a paginated list of active products in that category (empty list if none).

### User Story P2: View Product Details
- **As a** Customer, **I want to** view a single product's details.
- **Scenario:**
  i. The user requests a product by ID from Product Service.
  ii. **Inter-Service Validation:** Product Service sends a REST request to Inventory Service to retrieve the current stock level for that product.
  iii. Product Service returns price, description, images, and the stock indicator; if the product ID does not exist, it returns HTTP 404.

### User Story P3: Search Products
- **As a** Customer, **I want to** search products by keyword.
- **Scenario:**
  i. The user submits a keyword to Product Service.
  ii. Product Service matches the keyword against product names/descriptions and returns ranked results (empty list if none, not an error).

### User Story P4: Create / Update / Deactivate a Product
- **As an** Admin, **I want to** create, update, and deactivate products.
- **Scenario:**
  i. The Admin submits product data (create, update, or deactivate) to Product Service.
  ii. Product Service verifies the caller has the Admin role (rejects with HTTP 403 otherwise).
  iii. Product Service persists the change; Customers immediately see the updated/removed product on their next browse or search.
