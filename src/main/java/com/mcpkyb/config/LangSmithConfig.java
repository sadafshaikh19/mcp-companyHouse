package com.mcpkyb.config;

import com.mcpkyb.service.LangSmithListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangSmithConfig {

    @Value("${langsmith.api.key:}")
    private String langSmithApiKey;

    @Value("${langsmith.project.name:mcp-kyb-langchain}")
    private String projectName;

    @Value("${langsmith.enabled:true}")
    private boolean langSmithEnabled;

    @Bean
    public LangSmithListener langSmithTracingListener() {
        if (langSmithEnabled && langSmithApiKey != null && !langSmithApiKey.isEmpty()) {
            System.out.println("✅ LangSmith integration enabled - API Key configured, project: " + projectName);

            // Try to create actual LangSmith client
            try {
                Class<?> langSmithClass = Class.forName("com.langchain.smith.LangSmith");
                Object langSmithInstance = langSmithClass.getConstructor(String.class).newInstance(langSmithApiKey);

                // Try to create tracing listener
                Class<?> listenerClass = Class.forName("com.langchain.smith.tracing.LangSmithTracingListener");
                Object listenerInstance = listenerClass.getConstructor(langSmithClass, String.class)
                    .newInstance(langSmithInstance, projectName);

                System.out.println("🎯 LangSmith tracing listener created successfully!");
                return new LangSmithListenerWrapper(listenerInstance);

            } catch (ClassNotFoundException e) {
                System.out.println("⚠️ LangSmith classes not found - using local monitoring only");
                System.out.println("💡 To enable LangSmith tracing:");
                System.out.println("   1. Check for updated LangSmith Java SDK");
                System.out.println("   2. Or use LangSmith REST API manually");
                return new LangSmithListenerWrapper(null);
            } catch (Exception e) {
                System.out.println("⚠️ Failed to initialize LangSmith: " + e.getMessage());
                return new LangSmithListenerWrapper(null);
            }
        }
        System.out.println("ℹ️ LangSmith integration disabled - no API key configured or disabled in properties");
        return null;
    }

    /**
     * Wrapper class to adapt the LangSmith listener to our interface
     */
    private static class LangSmithListenerWrapper implements LangSmithListener {
        private final Object wrappedListener;

        public LangSmithListenerWrapper(Object wrappedListener) {
            this.wrappedListener = wrappedListener;
        }

        public Object getWrappedListener() {
            return wrappedListener;
        }
    }
}
