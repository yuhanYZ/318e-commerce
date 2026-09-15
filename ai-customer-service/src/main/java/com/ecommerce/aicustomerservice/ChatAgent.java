package com.ecommerce.aicustomerservice;

/**
 * Phase 3 test seam (see AiCustomerServiceContractTests): a single-abstract-method interface so
 * the contract test can supply a deterministic {@code @Primary} stub bean
 * ({@code (sessionId, userMessage) -> "This is a stubbed AI reply for testing."}) without network
 * access or an API key. Declared top-level in this package (not a sub-package) because the test
 * class references {@code ChatAgent} with no import statement.
 *
 * The real LangChain4j-backed implementation is {@code ai.ChatAgentConfig}'s {@code chatAgent}
 * bean, active on every profile except "test" (see that class for why).
 */
@FunctionalInterface
public interface ChatAgent {

    /**
     * @param sessionId the conversation session the message belongs to (grounding context only —
     *                   this method intentionally carries no caller bearer token, since the test
     *                   seam's signature is fixed to exactly these two String parameters; see
     *                   ai.ProductLookupTool's Javadoc for what that means for tool-call scope)
     * @param userMessage the customer's message text
     * @return the AI's reply text
     */
    String reply(String sessionId, String userMessage);
}
