# User Stories — User Service

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review
**Source:** Split from [2-user-stories.md](./2-user-stories.md)

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| User Service | U1 | Register an Account | Customer |
| User Service | U2 | Login | Customer |
| User Service | U3 | View / Update Profile | Customer |
| User Service | U4 | Reset Password | Customer |
| User Service | U5 | Deactivate Account | Customer |

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
  iii. If valid, User Service issues a JWT auth token; after 5 consecutive failures within 10 minutes, the account is temporarily locked.

### User Story U3: View / Update Profile
- **As a** Customer, **I want to** view and update my profile (name, address, phone).
- **Scenario:**
  i. The authenticated user requests their profile from User Service.
  ii. User Service returns the current profile data.
  iii. To update, the user submits changed fields; User Service validates and persists them.

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
