package com.smarttaskmanager.taskcapture.provider;

import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.taskcapture.dto.ParsedTaskResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * OpenAI-based task capture provider.
 * Uses GPT to parse natural language into structured task data via the OpenAI REST API.
 * Falls back gracefully if the API key is not configured or the API fails.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "OPENAI_API_KEY", matchIfMissing = false)
public class OpenAiTaskCaptureProvider implements TaskCaptureProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;
    private final boolean enabled;

    public OpenAiTaskCaptureProvider(
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${app.ai.model:gpt-4o-mini}") String model) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.model = model;
        this.apiKey = apiKey;
        this.enabled = apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ParsedTaskResult parse(String input, String timezone) {
        if (!enabled) {
            log.warn("OpenAI provider is not configured (missing OPENAI_API_KEY). This provider cannot parse tasks.");
            throw new IllegalStateException("OpenAI provider is not configured. Set OPENAI_API_KEY environment variable.");
        }

        String systemPrompt = buildSystemPrompt(timezone);
        try {
            String response = callOpenAi(systemPrompt, input);
            return parseJsonResponse(response, timezone);
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}. Falling back to rule-based parser.", e.getMessage());
            throw new IllegalStateException("OpenAI API call failed: " + e.getMessage());
        }
    }

    @Override
    public String getType() {
        return "openai";
    }

    private String buildSystemPrompt(String timezone) {
        String tz = (timezone != null && !timezone.isBlank()) ? timezone : "UTC";
        return """
                You are a task parsing assistant. Parse the user's natural language input into structured task data.
                Return ONLY valid JSON, no markdown, no explanation.
                
                Current timezone: %s
                
                Rules:
                - "title": A concise task title (max 150 chars)
                - "description": Additional details if present, null otherwise
                - "status": One of TODO, IN_PROGRESS, COMPLETED, CANCELLED (default: TODO)
                - "priority": One of LOW, MEDIUM, HIGH, URGENT (default: MEDIUM)
                - "dueDate": ISO 8601 date string in the user's timezone, or null if not specified
                - "categoryName": Category name if identifiable, null otherwise
                - "estimatedDurationMinutes": Integer minutes if specified, null otherwise
                - "recurrenceType": One of DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS, CUSTOM_WEEKLY, or null
                - "recurrenceInterval": Integer interval for recurrence, null if not recurring
                - "ambiguities": A string explaining any uncertain interpretations, or null if clear
                
                Interpret dates relative to today in the user's timezone.
                If the input is unclear, set ambiguities to explain what is uncertain.
                """.formatted(tz);
    }

    private String callOpenAi(String systemPrompt, String userMessage) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // Build request body
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.0);

        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        body.set("messages", messages);

        HttpEntity<ObjectNode> request = new HttpEntity<>(body, headers);
        String response = restTemplate.exchange(url, HttpMethod.POST, request, String.class).getBody();

        if (response == null) {
            throw new IllegalStateException("Empty response from OpenAI API");
        }

        // Extract the assistant's message content
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").get(0).path("message").path("content");
        return content.asText("");
    }

    private ParsedTaskResult parseJsonResponse(String json, String timezone) {
        try {
            // Strip markdown code blocks if present
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                int endIdx = cleaned.indexOf("```", 3);
                if (endIdx > 0) {
                    cleaned = cleaned.substring(0, endIdx).trim();
                    if (cleaned.startsWith("json")) {
                        cleaned = cleaned.substring(4).trim();
                    }
                }
            }

            JsonNode root = objectMapper.readTree(cleaned);

            String title = root.has("title") && !root.get("title").isNull() ? root.get("title").asText() : null;
            String description = root.has("description") && !root.get("description").isNull() ? root.get("description").asText() : null;

            TaskStatus status = TaskStatus.TODO;
            if (root.has("status") && !root.get("status").isNull()) {
                try {
                    status = TaskStatus.valueOf(root.get("status").asText());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status from AI: {}", root.get("status").asText());
                }
            }

            TaskPriority priority = TaskPriority.MEDIUM;
            if (root.has("priority") && !root.get("priority").isNull()) {
                try {
                    priority = TaskPriority.valueOf(root.get("priority").asText());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid priority from AI: {}", root.get("priority").asText());
                }
            }

            Instant dueDate = null;
            if (root.has("dueDate") && !root.get("dueDate").isNull()) {
                try {
                    String dueDateStr = root.get("dueDate").asText();
                    ZoneId zoneId = resolveZoneId(timezone);
                    dueDate = parseDateFlexible(dueDateStr, zoneId);
                } catch (Exception e) {
                    log.warn("Could not parse due date from AI: {}", e.getMessage());
                }
            }

            String categoryName = root.has("categoryName") && !root.get("categoryName").isNull()
                    ? root.get("categoryName").asText() : null;

            Integer estimatedDurationMinutes = root.has("estimatedDurationMinutes") && !root.get("estimatedDurationMinutes").isNull()
                    ? root.get("estimatedDurationMinutes").asInt() : null;

            RecurrenceType recurrenceType = null;
            if (root.has("recurrenceType") && !root.get("recurrenceType").isNull()) {
                try {
                    recurrenceType = RecurrenceType.valueOf(root.get("recurrenceType").asText());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid recurrence type from AI: {}", root.get("recurrenceType").asText());
                }
            }

            Integer recurrenceInterval = root.has("recurrenceInterval") && !root.get("recurrenceInterval").isNull()
                    ? root.get("recurrenceInterval").asInt() : null;

            String ambiguities = root.has("ambiguities") && !root.get("ambiguities").isNull()
                    ? root.get("ambiguities").asText() : null;

            if (title == null || title.isBlank()) {
                title = "Untitled task";
                if (ambiguities != null) {
                    ambiguities += " Title could not be extracted.";
                } else {
                    ambiguities = "Title could not be extracted from the input.";
                }
            }

            return new ParsedTaskResult(
                    title, description, status, priority, dueDate,
                    categoryName, estimatedDurationMinutes,
                    recurrenceType, recurrenceInterval, ambiguities
            );
        } catch (Exception e) {
            log.error("Failed to parse OpenAI JSON response: {}", e.getMessage());
            throw new IllegalStateException("Failed to parse AI response: " + e.getMessage());
        }
    }

    private Instant parseDateFlexible(String dateStr, ZoneId zoneId) {
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            try {
                LocalDate date = LocalDate.parse(dateStr);
                return date.atStartOfDay(zoneId).toInstant();
            } catch (Exception e2) {
                throw new IllegalArgumentException("Could not parse date: " + dateStr);
            }
        }
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }
}
