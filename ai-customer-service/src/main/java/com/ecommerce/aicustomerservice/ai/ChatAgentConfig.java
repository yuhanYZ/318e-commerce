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

/**
 * The real AI1/AI2 flow: an OpenAI-compatible chat model via LangChain4j, grounded with
 * {@link ProductLookupTool}. Defaults to real OpenAI (GPT-4o-mini) but {@code app.openai.base-url}
 * can point this at any OpenAI-compatible provider (e.g. DeepSeek's {@code
 * https://api.deepseek.com}) instead — see application.yml's comment for the env vars to override.
 *
 * {@code @Profile("!test")} keeps this bean out of the Spring context entirely under the
 * "test" profile that AiCustomerServiceContractTests activates (`@ActiveProfiles("test")`) — so
 * `mvn test` never attempts to build an OpenAiChatModel (no network call, no API key needed) and
 * the test's own `@Primary` stub `ChatAgent` bean is the only one that exists in that context.
 */
@Configuration
public class ChatAgentConfig {

    @Bean
    @Profile("!test")
    public ChatAgent chatAgent(@Value("${app.openai.api-key}") String apiKey,
                                @Value("${app.openai.base-url}") String baseUrl,
                                @Value("${app.openai.model-name}") String modelName,
                                ProductLookupTool productLookupTool) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(productLookupTool)
                .build();

        return (sessionId, userMessage) -> assistant.chat(userMessage);
    }

    /** Internal LangChain4j AiServices contract — not exposed outside this configuration class. */
    interface Assistant {
        @SystemMessage("""
                You are a helpful e-commerce customer service assistant. Answer questions about
                products using the getProduct tool when a product is mentioned. If you cannot find
                grounded data to answer confidently, say so plainly rather than guessing.
                """)
        String chat(@UserMessage String userMessage);
    }
}
