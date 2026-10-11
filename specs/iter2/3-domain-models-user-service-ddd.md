# Domain Models Specification - User Service

**Project:** 318e-e-commerce
**Status:** Final
**Version:** 2.0
**Date:** 2026-10-10

Evolution from `specs/iter1/3-domain-models.md` (Iteration 1, archived for history):
- `User` retained as the aggregate root, now expressed with typed value objects instead of primitive fields.
- `PasswordResetToken` promoted to its own small aggregate (referenced by `UserId`, not composed inside `User`).
- Implicit login-lockout logic (`failedLoginAttempts` counter) made explicit as an `AuthenticationService` domain service plus an `AccountLocked` domain event.

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `User` | Aggregate Root | Registered account (Customer or Admin); owns profile, credentials, and status |
| `PasswordResetToken` | Aggregate Root | Time-limited token issued for a password reset request |
| `UserId` | Value Object | Typed, validated user identifier |
| `Email` | Value Object | Validated, unique login email |
| `PasswordHash` | Value Object | Hashed password; never exposes plaintext |
| `ContactInfo` | Value Object | Encapsulates `address` and `phone` together |
| `Role` | Value Object | `CUSTOMER`, `ADMIN` |
| `AccountStatus` | Value Object | `ACTIVE`, `LOCKED`, `DEACTIVATED` |
| `ResetToken` | Value Object | Randomly generated, validated reset token string |
| `UserRegistered` | Domain Event | Emitted by `UserAggregate` on successful registration |
| `UserLoggedIn` | Domain Event | Emitted by `UserAggregate` on successful login |
| `AccountLocked` | Domain Event | Emitted by `UserAggregate` after 5 failed logins within 10 minutes |
| `ProfileUpdated` | Domain Event | Emitted by `UserAggregate` when name/address/phone is changed |
| `AccountDeactivated` | Domain Event | Emitted by `UserAggregate` on soft-delete (U5) |
| `PasswordResetRequested` | Domain Event | Emitted by `PasswordResetTokenAggregate` on token issuance |
| `PasswordResetCompleted` | Domain Event | Emitted by `PasswordResetTokenAggregate` when a token is consumed |
| `AuthenticationService` | Domain Service | Verifies credentials, tracks failed attempts, applies lockout policy |
| `PasswordResetService` | Domain Service | Issues reset tokens, validates expiry/replay before allowing a reset |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **U1. Register an Account** | `User`, `Email`, `PasswordHash` | `UserRegistered` |
| **U2. Login** | `User`, `AuthenticationService` | `UserLoggedIn`, `AccountLocked` (after 5 failures) |
| **U3a. View Profile** | `User`, `ContactInfo` | None |
| **U3b. Update Profile** | `User`, `ContactInfo` | `ProfileUpdated` |
| **U4. Reset Password** | `PasswordResetToken`, `PasswordResetService`, `User` | `PasswordResetRequested`, `PasswordResetCompleted` |
| **U5. Deactivate Account** | `User` | `AccountDeactivated` |

---

## 2. DDD Building Blocks

### 2.1 Entities

*(none — all identity-bearing objects in this service are aggregate roots; see 2.3)*

---

### 2.2 Value Objects

#### 2.2.1 `UserId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `Email`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` | Validated email address; uniqueness enforced at the aggregate boundary |

#### 2.2.3 `PasswordHash`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` | Hashed password; constructed only from a hashing function, never from plaintext |

#### 2.2.4 `ContactInfo`

| Field | Type | Description |
| :--- | :--- | :--- |
| `address` | `String` | Delivery address |
| `phone` | `String` | Contact number |

#### 2.2.5 `Role`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `CUSTOMER`, `ADMIN` |

#### 2.2.6 `AccountStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `ACTIVE`, `LOCKED`, `DEACTIVATED` |

#### 2.2.7 `ResetToken`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` | Randomly generated token string |

---

### 2.3 Aggregates

#### 2.3.1 `UserAggregate` (root: `User`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `UserId` | Unique user identifier | Primary identifier |
| `email` | `Email` | Login email | Required, unique |
| `passwordHash` | `PasswordHash` | Hashed password | Required |
| `name` | `String` | Display name | Editable via U3b |
| `contact` | `ContactInfo` | Address and phone | Editable via U3b |
| `role` | `Role` | Account role | Required |
| `status` | `AccountStatus` | Current account status | Default: `ACTIVE` |
| `failedLoginAttempts` | `int` | Consecutive failed login count | Reset on successful login |
| `createdAt` | `LocalDateTime` | Account creation timestamp | Auto-generated |

**Invariants**
1. `email` is unique across all `User` instances.
2. `status` transitions to `LOCKED` only after 5 failed logins within a 10-minute window.
3. `status = DEACTIVATED` is terminal — no further login is possible once set.

**Behaviors**
- `register(email, passwordHash, name, contact)` — emits `UserRegistered`
- `login(passwordHash, now)` — emits `UserLoggedIn`, or `AccountLocked` when the failure threshold is crossed
- `updateProfile(name, contact)` — emits `ProfileUpdated`
- `deactivate()` — emits `AccountDeactivated`

#### 2.3.2 `PasswordResetTokenAggregate` (root: `PasswordResetToken`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique token record identifier | Primary identifier |
| `userId` | `UserId` | Reference to the `User` | Required, cross-aggregate ID only |
| `token` | `ResetToken` | Randomly generated reset token | Required, unique |
| `expiresAt` | `LocalDateTime` | Expiry timestamp | Business calculation |
| `used` | `boolean` | Whether the token has been consumed | Default: `false`, prevents replay |

**Invariants**
1. A token can be consumed at most once (`used` flips `false → true` and stays there).
2. A token cannot be consumed after `expiresAt`.

**Behaviors**
- `issue(userId, expiresAt)` — emits `PasswordResetRequested`
- `consume(now)` — emits `PasswordResetCompleted`

---

### 2.4 Domain Services

#### 2.4.1 `AuthenticationService`

| Method | Parameters | Returns | Description |
| :--- | :--- | :--- | :--- |
| `verifyCredentials` | `user: User, passwordHash: PasswordHash` | `boolean` | Checks the supplied hash against the stored one |
| `recordFailedAttempt` | `user: User, now: LocalDateTime` | `AccountStatus` | Increments failure count, applies lockout policy (5 failures / 10 min) |
| `resetFailureCount` | `user: User` | `void` | Clears `failedLoginAttempts` on successful login |

#### 2.4.2 `PasswordResetService`

| Method | Parameters | Returns | Description |
| :--- | :--- | :--- | :--- |
| `generateToken` | `userId: UserId` | `PasswordResetToken` | Creates a new token with a business-calculated expiry |
| `validateToken` | `token: PasswordResetToken, now: LocalDateTime` | `boolean` | Checks the token is neither used nor expired |

---

### 2.5 Domain Events

#### 2.5.1 `UserRegistered`

| Field | Type | Description |
| :--- | :--- | :--- |
| `userId` | `UserId` | The newly created account |
| `email` | `Email` | Registered email |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `UserLoggedIn`

| Field | Type | Description |
| :--- | :--- | :--- |
| `userId` | `UserId` | The authenticated account |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `AccountLocked`

| Field | Type | Description |
| :--- | :--- | :--- |
| `userId` | `UserId` | The locked account |
| `failedAttempts` | `int` | Failure count at the time of locking |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.4 `ProfileUpdated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `userId` | `UserId` | The updated account |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.5 `AccountDeactivated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `userId` | `UserId` | The deactivated account |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.6 `PasswordResetRequested`

| Field | Type | Description |
| :--- | :--- | :--- |
| `tokenId` | `String` | The issued token record |
| `userId` | `UserId` | The account requesting reset |
| `expiresAt` | `LocalDateTime` | Token expiry |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.7 `PasswordResetCompleted`

| Field | Type | Description |
| :--- | :--- | :--- |
| `tokenId` | `String` | The consumed token record |
| `userId` | `UserId` | The account whose password was reset |
| `occurredAt` | `LocalDateTime` | Event timestamp |
