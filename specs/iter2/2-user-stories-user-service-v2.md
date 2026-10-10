# User Stories — User Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10
**Source:** Split from [iter1/2-user-stories.md](../iter1/2-user-stories.md) — Iteration 1, archived for history.

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| User Service | U1 | Register an Account | Customer |
| User Service | U2 | Login | Customer |
| User Service | U3a | View Profile | Customer |
| User Service | U3b | Update Profile | Customer |
| User Service | U4 | Reset Password | Customer |
| User Service | U5 | Deactivate Account | Customer |

---

## Roles and Admin Provisioning

- Registration (U1) always creates a `CUSTOMER` account. There is no self-service path to the `ADMIN` role.
- User Service seeds exactly one `ADMIN` account at startup from configuration (`app.admin.email`, `app.admin.password`); the seed is idempotent and skipped if that email already exists.
- Every issued JWT (U2) is signed with HS256 using a shared secret configured in all six services (`app.jwt.secret`) and carries `sub = userId` and `role` claims. Downstream services enforce Admin-only endpoints by checking the `role` claim — no callback to User Service is needed.

---

## User Service

### User Story U1: Register an Account
- **As a** Customer, **I want to** register an account using my email and password.
- **Scenario:**
  i. The user submits an email and password to User Service.
  ii. User Service validates the email format and password complexity, and checks the email is not already registered.
  iii. If valid, User Service creates the account and returns the new user ID; if the email is already taken, the request is rejected (HTTP 409).

### User Story U2: Login
- **As a** Customer, **I want to** log in with my email and password.
- **Scenario:**
  i. The user submits email and password to User Service.
  ii. User Service verifies the credentials against the stored account.
  iii. If valid, User Service issues a JWT auth token and resets the failure counter; after 5 consecutive failures within 10 minutes, the account is locked (HTTP 423 `AccountLocked`) for 10 minutes, after which login is permitted again and the counter restarts. A `DEACTIVATED` account can never log in (HTTP 401).

### User Story U3a: View Profile
- **As a** Customer, **I want to** view my profile (name, address, phone).
- **Scenario:**
  i. The authenticated user requests their profile from User Service.
  ii. User Service returns the current profile data.

### User Story U3b: Update Profile
- **As a** Customer, **I want to** update my profile (name, address, phone).
- **Scenario:**
  i. The authenticated user submits changed fields (name, address, and/or phone) to User Service.
  ii. User Service validates and persists them, and returns the updated profile.

### User Story U4: Reset Password
- **As a** Customer, **I want to** reset my password when I forget it.
- **Scenario:**
  i. The user requests a password reset for their registered email.
  ii. User Service issues a time-limited reset token.
  iii. The user submits the token with a new password; User Service validates the token and updates the password (rejects if expired/invalid).

### User Story U5: Deactivate Account
- **As a** Customer, **I want to** deactivate my account without losing my order history.
- **Scenario:**
  i. The authenticated user requests account deactivation.
  ii. User Service marks the account inactive (soft delete) rather than removing it.
  iii. **Inter-Service Note:** Order Service order history referencing this user ID remains intact and queryable.
