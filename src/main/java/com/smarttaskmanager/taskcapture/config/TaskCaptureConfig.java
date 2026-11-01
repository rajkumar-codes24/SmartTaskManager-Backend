package com.smarttaskmanager.taskcapture.config;

import com.smarttaskmanager.taskcapture.provider.OpenAiTaskCaptureProvider;
import com.smarttaskmanager.taskcapture.provider.RuleBasedTaskCaptureProvider;
import com.smarttaskmanager.taskcapture.provider.TaskCaptureProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for task capture provider selection.
 * Chooses between OpenAI and rule-based parsing based on environment variables.
 */
@Slf4j
@Configuration
public class TaskCaptureConfig {

    @Value("${app.ai.provider:rulebased}")
    private String providerType;

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Bean
    @Primary
    public TaskCaptureProvider taskCaptureProvider(
            RuleBasedTaskCaptureProvider ruleBasedProvider,
            org.springframework.beans.factory.ObjectProvider<OpenAiTaskCaptureProvider> openAiProviderProvider) {

        String effectiveProvider = providerType.toLowerCase().trim();

        // If OpenAI is requested but no API key is configured, fall back to rule-based
        if ("openai".equals(effectiveProvider)) {
            OpenAiTaskCaptureProvider openAiP = openAiProviderProvider.getIfAvailable();
            if (openAiP == null || openAiApiKey == null || openAiApiKey.isBlank()) {
                log.warn("OpenAI provider requested but OPENAI_API_KEY is not configured. Falling back to rule-based parser.");
                return ruleBasedProvider;
            }
            log.info("Using OpenAI task capture provider");
            return openAiP;
        }

        log.info("Using rule-based task capture provider");
        return ruleBasedProvider;
    }
}
