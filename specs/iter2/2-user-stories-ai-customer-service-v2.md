# User Stories — AI-Customer Service

**Project:** 318e-e-commerce (CSCI318 Capstone)
**Status:** Draft — for team review
**Source:** Split from [2-user-stories.md](./2-user-stories.md)

## User Story Overview

| Service | User Story ID | User Story Name | Role(s) |
|---|---|---|---|
| AI-Customer Service | AI1 | Ask a Product / Order Question | Customer |
| AI-Customer Service | AI2 | Get Return / Exchange Help | Customer |
| AI-Customer Service | AI3 | Escalate to Human Agent | Customer |
| AI-Customer Service | AI4 | Review AI Conversation Logs | Admin |

---

## AI-Customer Service

### Session Lifecycle (applies to AI1–AI3)
- There is no dedicated "create session" endpoint. The client generates a `sessionId` (UUID) and sends the first message to `POST /ai-chat/sessions/{sessionId}/messages`; if no session with that id exists, AI-Customer Service creates it for the authenticated user (`ConversationSessionAggregate.start(userId)`, emitting `SessionStarted`) before posting the message.
- A `sessionId` that exists but belongs to a different user is answered with HTTP 404 (never 403, to avoid leaking session existence).
- No endpoint closes a session in this milestone; `CLOSED` is reserved for future use.

### User Story AI1: Ask a Product / Order Question
- **As a** Customer, **I want to** ask natural-language questions about products or my orders.
- **Scenario:**
  i. The user sends a question to AI-Customer Service (creating the session on first use, see above).
  ii. **Inter-Service Validation:** AI-Customer Service (via LangChain4j tool calls) sends REST requests to Product Service and/or Order Service to retrieve relevant, grounded data, forwarding the customer's bearer token so Order Service's ownership check applies.
  iii. The LLM (GPT-4o-mini) composes an answer from the retrieved data, or states it cannot answer confidently if no grounded data is found.

### User Story AI2: Get Return / Exchange Help
- **As a** Customer, **I want to** ask about return/exchange eligibility and process.
- **Scenario:**
  i. The user asks about returning or exchanging an order.
  ii. **Inter-Service Validation:** AI-Customer Service queries Order Service for the order's status and date to determine eligibility per policy.
  iii. If eligible, the agent explains the process and can flag/initiate the return; if not, it explains why.

### User Story AI3: Escalate to Human Agent
- **As a** Customer, **I want to** escalate to a human agent when the AI can't resolve my issue.
- **Scenario:**
  i. The AI agent is unable to resolve the query after reasonable attempts, or the user explicitly requests a human.
  ii. AI-Customer Service flags/routes the conversation for human follow-up.

### User Story AI4: Review AI Conversation Logs
- **As an** Admin, **I want to** review past AI customer service conversations.
- **Scenario:**
  i. The Admin requests conversation logs from AI-Customer Service.
  ii. AI-Customer Service returns stored conversation history for quality/audit review.
