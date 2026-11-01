package com.smarttaskmanager.security.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttaskmanager.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles access denied exceptions for authenticated users who lack permission,
 * returning a structured {@link ErrorResponse} with HTTP 403 status.
 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.warn("Access denied for authenticated user on path {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                request.getRequestURI()
        );

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        try {
            JSON_MAPPER.writeValue(response.getOutputStream(), errorResponse);
        } catch (JsonProcessingException e) {
            response.getOutputStream().write("{\"error\":\"Access denied\"}".getBytes());
        }
    }
}
