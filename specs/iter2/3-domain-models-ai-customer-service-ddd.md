# Domain Models Specification - AI-Customer Service

Evolution from `specs/3-domain-models.md`:
- `ConversationSession` promoted to an aggregate root; `ConversationMessage` remains composed inside it (not an independent aggregate).
- Implicit status changes made explicit as aggregate behaviors, each emitting its own domain event.

---

## 1. Domain Classes and Related User Stories

### Domain Classes

| Domain Class | Type | Description |
| :--- | :--- | :--- |
| `ConversationSession` | Aggregate Root | A single customer-service conversation |
| `ConversationMessage` | Entity | A single message within a `ConversationSession`; composition, not an independent aggregate |
| `SessionId` | Value Object | Typed, validated session identifier |
| `UserId` | Value Object | Typed reference to a `User` (cross-service ID only) |
| `Sender` | Value Object | `USER`, `AI`, `AGENT` |
| `SessionStatus` | Value Object | `ACTIVE`, `ESCALATED`, `CLOSED` |
| `SessionStarted` | Domain Event | Emitted by `ConversationSessionAggregate` when a session begins |
| `MessageSent` | Domain Event | Emitted by `ConversationSessionAggregate` when a message is posted (AI1, AI2) |
| `SessionEscalated` | Domain Event | Emitted by `ConversationSessionAggregate` when escalated to a human agent (AI3) |
| `SessionClosed` | Domain Event | Emitted by `ConversationSessionAggregate` when the conversation ends |

### User Story Mapping

| User Story | Primary Relevant Domain Classes | Triggering Domain Events |
| :--- | :--- | :--- |
| **AI1. Ask a Product / Order Question** | `ConversationSession`, `ConversationMessage` | `MessageSent` |
| **AI2. Get Return / Exchange Help** | `ConversationSession`, `ConversationMessage` | `MessageSent` |
| **AI3. Escalate to Human Agent** | `ConversationSession` | `SessionEscalated` |
| **AI4. Review AI Conversation Logs** | `ConversationSession`, `ConversationMessage` | None |

---

## 2. DDD Building Blocks

### 2.1 Entities

#### 2.1.1 `ConversationMessage`

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `String` / `UUID` | Unique message identifier | Primary identifier within the aggregate |
| `sender` | `Sender` | Who sent the message | Required |
| `content` | `String` | Message text | Required |
| `referencedData` | `JSON` | Snapshot of cross-service data (Product/Order) the AI used to answer | Nullable, used for audit in AI4 |
| `createdAt` | `LocalDateTime` | Message timestamp | Auto-generated |

---

### 2.2 Value Objects

#### 2.2.1 `SessionId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier |

#### 2.2.2 `UserId`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `String` / `UUID` | Underlying identifier of the customer in the session |

#### 2.2.3 `Sender`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `USER`, `AI`, `AGENT` |

#### 2.2.4 `SessionStatus`

| Field | Type | Description |
| :--- | :--- | :--- |
| `value` | `Enum` | `ACTIVE`, `ESCALATED`, `CLOSED` |

---

### 2.3 Aggregates

#### 2.3.1 `ConversationSessionAggregate` (root: `ConversationSession`)

| Field | Type | Description | Constraints |
| :--- | :--- | :--- | :--- |
| `id` | `SessionId` | Unique session identifier | Primary identifier |
| `userId` | `UserId` | Reference to the `User` | Required, cross-service ID only |
| `status` | `SessionStatus` | Current session status | Default: `ACTIVE` |
| `messages` | `List<ConversationMessage>` | Messages in the session | Ordered by `createdAt` |
| `createdAt` | `LocalDateTime` | Session start timestamp | Auto-generated |
| `updatedAt` | `LocalDateTime` | Last activity timestamp | Auto-updated |

**Invariants**
1. Status transitions are restricted to: `ACTIVE → ESCALATED`, `{ACTIVE, ESCALATED} → CLOSED`.
2. `CLOSED` is terminal — no further messages can be posted once closed.

**Behaviors**
- `start(userId)` — emits `SessionStarted`
- `postMessage(sender, content, referencedData)` — emits `MessageSent`
- `escalate()` — emits `SessionEscalated`
- `close()` — emits `SessionClosed`

---

### 2.4 Domain Services

*(none — session lifecycle rules are simple enough to live entirely on the aggregate)*

---

### 2.5 Domain Events

#### 2.5.1 `SessionStarted`

| Field | Type | Description |
| :--- | :--- | :--- |
| `sessionId` | `SessionId` | The newly started session |
| `userId` | `UserId` | The customer starting the session |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.2 `MessageSent`

| Field | Type | Description |
| :--- | :--- | :--- |
| `sessionId` | `SessionId` | The session the message belongs to |
| `messageId` | `String` | The posted message |
| `sender` | `Sender` | Who sent the message |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.3 `SessionEscalated`

| Field | Type | Description |
| :--- | :--- | :--- |
| `sessionId` | `SessionId` | The escalated session |
| `occurredAt` | `LocalDateTime` | Event timestamp |

#### 2.5.4 `SessionClosed`

| Field | Type | Description |
| :--- | :--- | :--- |
| `sessionId` | `SessionId` | The closed session |
| `occurredAt` | `LocalDateTime` | Event timestamp |
