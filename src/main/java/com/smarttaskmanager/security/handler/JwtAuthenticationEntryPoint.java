package com.smarttaskmanager.security.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttaskmanager.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles unauthenticated access attempts and returns a structured {@link ErrorResponse}
 * with HTTP 401 status instead of Spring Security's default response.
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        log.warn("Unauthorized access attempt on path {}: {}", request.getRequestURI(), authException.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource",
                request.getRequestURI()
        );

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        try {
            JSON_MAPPER.writeValue(response.getOutputStream(), errorResponse);
        } catch (JsonProcessingException e) {
            response.getOutputStream().write("{\"error\":\"Authentication required\"}".getBytes());
        }
    }
}
