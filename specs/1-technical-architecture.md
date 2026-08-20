## Microservice Architecture

This application contains six Microservice Modules:

- **User Service**: Handles user registration, login, authentication, and profile management.
- **Product Service**: Manages product catalog, product details, and categories.
- **Order Service**: Processes order workflows, order status tracking, and order queries.
- **Inventory Service**: Manages stock quantities, low-stock alerts, and real-time inventory queries (stream processing).
- **Payment Service**: Handles payment processing, payment status tracking, and refunds.
- **AI-Customer Service**: LLM-based intelligent customer support, handling inquiries and return/exchange Q&A(Agentic AI)

## Layered Architecture

Each microservice module is structured into four distinct layers:

#### **Presentation Layer** (Controllers) 

**Responsibilities:** Receive HTTP requests → Perform basic input format validation → Call Service Layer → Package results into HTTP responses and return them. Do not write business logic.

#### **Service Layer** (Business Logic)

**Responsibilities:** Orchestration, not implementation. It is responsible for:

- Fetching data from the Repository
- Calling Domain layer methods to execute business rules
- Persisting the results back (via the Infrastructure layer)
- Cross-service coordination (e.g., Order Service calling Inventory Service to check stock)

#### Domain Layer(Core Domain Models & Rules)**************
（The Domain layer contains pure business logic only — no infrastructure concerns, no external calls. It's where our core business rules and invariants live. ）

#### **Infrastructure Layer** (Repositories, Kafka Producers/Consumers, LLM Clients) 

Persistence, message publishing/subscribing, external API calls



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
  - **Data Access Layer**: `@Repository`

- **Database**: H2 in-memory database for development

- **Messaging**: Apache Kafka (event-driven inter-service communication)

  Example: Order Service publishes `OrderCreatedEvent` → Inventory Service subscribes and deducts stock

- **Stream Processing**: Spring Cloud Stream

  - Used to implement real-time analysis requirements such as real-time inventory alerts and real-time order analytics.

- **Agentic AI**: LangChain4j

  - LLM Provider: GPT-4o-mini
  - base on AI-Customer Service automatic system

- **Unit & Integration Testing**: MockMvc, `@SpringBootTest`, JUnit 5

## Future Considerations

Distributed transactions (Saga Pattern) to handle cross-service consistency across order placement, stock deduction, and payment

API Gateway as the unified entry point
