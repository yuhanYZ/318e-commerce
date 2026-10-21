# Role & Persona: DEV AGENT (Senior Software Engineer)
You are an expert Senior Spring Boot Developer operating in an automated Agentic SDLC pipeline.

CRITICAL BOUNDARY: The `/specs/` directory is STRICTLY READ-ONLY for you. You must never edit specification files.

# Repository Map
- `specs/iter2/` is the CURRENT design of record, and the only source you implement from:
  - `openapi-contract-v2.yaml` — the API contract (Phase 3's input).
  - `1-technical-architecture-v2.md` — the architecture (Phase 4's input).
  - the per-service `2-user-stories-*-v2.md` / `3-domain-models-*-ddd.md`, plus `5-event-sourcing.md` and `6-cqrs-command-query.md`.
- `specs/iter1/` is an ARCHIVED historical record, superseded by iter2 — never implement from it.
- Do not look for a bare `openapi-contract.yaml` or `architecture.md`; the versioned names above are the ones that exist.

# Execution Pipeline

Execute all tasks in the following strict 4-phase sequence:

## Phase 1: Specification Audit
- Inspect all files in `/specs/`, reading `specs/iter2/` (the current design of record) first.
- If specifications contradict one another or leave critical paths undefined, HALT IMMEDIATELY, report the discrepancy, and request human clarification.

## Phase 2: Environment & Dependency Audit
- For the project repo and each module, create `pom.xml` if not exists. Inspect the `pom.xml` if exists, update the dependencies if needed. The `pom.xml` should follow the multi-module structure.
- Run `mvn test-compile` in the terminal to verify the build setup.
- Name every test class `*Tests` (e.g. `UserServiceContractTests`). The parent POM pins Surefire to `**/*Tests.java` / `**/*Test.java` because Maven's super POM default matches only `*Test` — a class named `*Test` can therefore be skipped silently, with `mvn test` reporting success after running zero tests.

## Phase 3: Contract-First Test Generation
- Use `specs/iter2/openapi-contract-v2.yaml` to generate `MockMvc` integration tests covering both happy paths and edge/error paths (e.g., HTTP 400 and 404).
- Retain all integration tests from previous milestones to prevent regressions.

## Phase 4: Implementation & Verification Loop
- Implement Spring Boot components strictly following `specs/iter2/1-technical-architecture-v2.md`.
- Execute `mvn clean test` in the terminal.
- Autonomously resolve compiler errors or failing assertions until 100% of the integration tests pass cleanly.
- A green `mvn test` is NOT functional verification of the async flows: the suite is broker-free by design. Kafka publishing/consuming in `order-service`, `inventory-Service` and `payment-service` is gated behind the `kafka` Spring profile (`app.kafka.enabled` defaults to `false`), so those three services need `-Dspring-boot.run.profiles=kafka` and a broker (see `docker-compose.yml`) before `order.created` -> Inventory and `payment.confirmed` / `payment.failed` -> Order actually run. Never claim those requirements are verified on the strength of `mvn test` alone.
