# Technical Architecture (Iteration 2)

## Microservice Architecture
This application contains six **Microservice Modules**:
- **User Service**: Handles user registration, login, authentication, and profile management.
- **Product Service**: Manages product catalog, product details, and categories.
- **Order Service**: Processes order workflows, order status tracking, and order queries.
- **Inventory Service**: Manages stock quantities, low-stock alerts, and real-time inventory queries (stream processing).
- **Payment Service**: Handles payment processing, payment status tracking, and refunds.
- **AI-Customer Service**: LLM-based intelligent customer support, handling inquiries and return/exchange Q&A (Agentic AI).

## Layered Architecture
Each microservice module is structured into four distinct layers:
1. **Presentation Layer** (Controllers): Receives HTTP requests, performs basic input format validation, calls the Service Layer, and packages results into HTTP responses. Does not contain business logic.
2. **Service Layer** (Business Logic): Orchestration, not implementation. Responsible for fetching data from the Repository, calling Domain layer methods to execute business rules, persisting results back via the Infrastructure layer, and cross-service coordination (e.g., Order Service calling Inventory Service to check stock).
3. **Domain Layer** (Core Domain Models & Rules): Contains pure business logic only — no infrastructure concerns, no external calls. This is where core business rules and invariants live.
4. **Infrastructure Layer** (Repositories, Kafka Producers/Consumers, LLM Clients): Handles persistence, message publishing/subscribing, and external API calls.

## CQRS View (Inventory Service)
Inventory Service applies CQRS with logical separation inside the same service module:
1. **Command application service**: handles state-changing use cases (e.g., stock deduction triggered by `OrderCreatedEvent`), executes aggregate rules, and persists writes.
2. **Query application service**: handles read-only use cases from read models/projections, powering real-time inventory queries and low-stock alerts via Spring Cloud Stream.

## Repository Structure
The project is structured as a multi-module repository:
- `user-service`: Module for the User Service
- `product-service`: Module for the Product Service
- `order-service`: Module for the Order Service
- `inventory-service`: Module for the Inventory Service
- `payment-service`: Module for the Payment Service
- `ai-customer-service`: Module for the AI-Customer Service

## Technology Stack
- **JDK & Build**: Java 21, Apache Maven (Multi-module POM)
- **Framework**: Spring Boot
    - **Controller Layer**: `@RestController`
    - **Service Layer**: `@Service`
    - **Domain Layer**: `@Entity`
    - **Infrastructure Layer**: `@Repository`
- **Database**: H2 in-memory database for development (target: PostgreSQL)
- **Messaging**: Apache Kafka (event-driven inter-service communication)
    - Example: Order Service publishes `OrderCreatedEvent` → Inventory Service subscribes and deducts stock
- **Stream Processing**: Spring Cloud Stream
    - Used to implement real-time analysis requirements such as real-time inventory alerts and real-time order analytics
- **Agentic AI**: LangChain4j
    - LLM Provider: GPT-4o-mini
    - Powers the AI-Customer Service automated support system
- **Unit & Integration Testing**: `MockMvc`, `@SpringBootTest`, `JUnit 5`

