package com.mcpkyb.config;

import com.mcpkyb.service.LangSmithListener;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class LangChainConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model.name}")
    private String modelName;

    @Autowired(required = false)
    private LangSmithListener langSmithListener;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);

        // Add LangSmith listener if available
        if (langSmithListener != null) {
            try {
                // Try to access the wrapped listener via reflection
                java.lang.reflect.Method getWrappedListenerMethod = langSmithListener.getClass().getMethod("getWrappedListener");
                Object wrappedListener = getWrappedListenerMethod.invoke(langSmithListener);

                if (wrappedListener instanceof ChatModelListener) {
                    ChatModelListener listener = (ChatModelListener) wrappedListener;
                    builder.listeners(List.of(listener));
                } else {
                    System.err.println("Wrapped LangSmith listener is not a ChatModelListener, skipping");
                }
            } catch (Exception e) {
                // If reflection fails, skip the listener
                System.err.println("Failed to add LangSmith listener via reflection: " + e.getMessage());
            }
        }

        return builder.build();
    }
}

    