package com.ecommerce.aicustomerservice;

/**
 * Port for the AI1/AI2 conversational agent. The real implementation is
 * {@code ai.ChatAgentConfig}'s LangChain4j-backed bean; the contract test supplies a deterministic
 * {@code @Primary} stub so the API contract can be verified without network access or an API key.
 *
 * @param sessionId   the conversation session (grounding/context only)
 * @param userMessage the customer's message text
 * @param bearerToken the caller's own Authorization header, forwarded unchanged to Order Service so
 *                    its per-user ownership check (O2) still applies to whatever the agent reads —
 *                    the agent must never be able to read another customer's order
 * @return the AI's reply text
 */
@FunctionalInterface
public interface ChatAgent {

    String reply(String sessionId, String userMessage, String bearerToken);
}
