package com.ecommerce.aicustomerservice.ai;

import com.ecommerce.aicustomerservice.ChatAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * The real AI1/AI2 flow: an OpenAI-compatible chat model via LangChain4j, grounded with the
 * {@link ProductLookupTool} and {@link OrderLookupTool} tools.
 *
 * <p>The provider defaults to DeepSeek's OpenAI-compatible endpoint (see application.yml) and can
 * be repointed at real OpenAI with the {@code OPENAI_BASE_URL} / {@code OPENAI_MODEL} env vars.
 *
 * <p>The assistant is built per call because the order tool is token-bound: Order Service's
 * {@code GET /orders/{id}} enforces ownership, so the tool has to carry the calling user's bearer
 * token, which differs per request. The (expensive) chat model itself is built once, here.
 *
 * <p>{@code @Profile("!test")} keeps this bean out of the Spring context entirely under the "test"
 * profile that AiCustomerServiceContractTests activates — so {@code mvn test} never attempts to
 * build an OpenAiChatModel (no network call, no API key needed) and the test's own {@code @Primary}
 * stub {@code ChatAgent} bean is the only one that exists in that context.
 */
@Configuration
public class ChatAgentConfig {

    @Bean
    @Profile("!test")
    public ChatAgent chatAgent(@Value("${app.openai.api-key}") String apiKey,
                                @Value("${app.openai.base-url}") String baseUrl,
                                @Value("${app.openai.model-name}") String modelName,
                                ProductLookupTool productLookupTool,
                                RestTemplate orderRestTemplate,
                                @Value("${app.order-service.base-url}") String orderBaseUrl) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        return (sessionId, userMessage, bearerToken) -> AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(productLookupTool, new OrderLookupTool(orderRestTemplate, orderBaseUrl, bearerToken))
                .build()
                .chat(userMessage);
    }

    /** Internal LangChain4j AiServices contract — not exposed outside this configuration class. */
    interface Assistant {
        @SystemMessage("""
                You are a helpful e-commerce customer service assistant.
                Use the getProduct tool for questions about a product (name, price, stock).
                Use the getOrder tool for questions about the customer's own orders; always ground
                return/exchange eligibility in the order status you retrieve rather than guessing.
                If you cannot find grounded data to answer confidently, say so plainly.
                """)
        String chat(@UserMessage String userMessage);
    }
}
