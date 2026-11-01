package com.smarttaskmanager.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Validation failure returns 400 with structured field errors and masked passwords")
    void validationFailure_returnsStructuredErrorResponse() throws Exception {
        String invalidBody = """
                {
                    "title": "",
                    "email": "invalid-email",
                    "password": "short"
                }
                """;

        mockMvc.perform(post("/test/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/test/exceptions/validation"))
                .andExpect(jsonPath("$.validationErrors", hasSize(4)))
                // Verify password was masked for security
                .andExpect(jsonPath("$.validationErrors[?(@.field == 'password')].rejectedValue").value("[PROTECTED]"));
    }

    @Test
    @DisplayName("Malformed JSON returns 400 with MALFORMED_JSON code")
    void malformedJson_returns400() throws Exception {
        String badJson = "{ \"title\": unclosed string }";

        mockMvc.perform(post("/test/exceptions/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request or unreadable payload"));
    }

    @Test
    @DisplayName("ResourceNotFoundException returns 404 with RESOURCE_NOT_FOUND code")
    void resourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/exceptions/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found with id: '999'"));
    }

    @Test
    @DisplayName("DuplicateResourceException returns 409 with DUPLICATE_RESOURCE code")
    void duplicateResource_returns409() throws Exception {
        mockMvc.perform(get("/test/exceptions/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("Category already exists with name: 'Work'"));
    }

    @Test
    @DisplayName("BusinessRuleException returns 400 with BUSINESS_RULE_VIOLATION code")
    void businessRule_returns400() throws Exception {
        mockMvc.perform(get("/test/exceptions/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Cannot complete an already cancelled task"));
    }

    @Test
    @DisplayName("UnauthorizedAccessException returns 403 with ACCESS_DENIED code")
    void unauthorizedAccess_returns403() throws Exception {
        mockMvc.perform(get("/test/exceptions/unauthorized-access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("You do not own this resource"));
    }

    @Test
    @DisplayName("InvalidTokenException returns 401 with custom error code")
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/test/exceptions/invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Verification token has expired"));
    }

    @Test
    @DisplayName("RateLimitExceededException returns 429 with RATE_LIMIT_EXCEEDED code")
    void rateLimitExceeded_returns429() throws Exception {
        mockMvc.perform(get("/test/exceptions/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Please wait."));
    }

    @Test
    @DisplayName("Type mismatch returns 400 with TYPE_MISMATCH code")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/test/exceptions/type-mismatch/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value(containsString("Parameter 'id' should be of type 'Long'")));
    }

    @Test
    @DisplayName("Missing request parameter returns 400 with MISSING_PARAMETER code")
    void missingParam_returns400() throws Exception {
        mockMvc.perform(get("/test/exceptions/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Required request parameter 'requiredParam' is missing"));
    }

    @Test
    @DisplayName("Unsupported HTTP method returns 405 with METHOD_NOT_ALLOWED code")
    void methodNotAllowed_returns405() throws Exception {
        mockMvc.perform(post("/test/exceptions/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException returns 409 without leaking raw SQL details")
    void dataIntegrityViolation_returnsSanitized409() throws Exception {
        mockMvc.perform(get("/test/exceptions/db-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("DATABASE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(not(containsString("table=users"))))
                .andExpect(jsonPath("$.message").value(not(containsString("column=email"))));
    }

    @Test
    @DisplayName("Unexpected internal error returns 500 without leaking stack trace or exception class")
    void internalError_returnsSanitized500() throws Exception {
        mockMvc.perform(get("/test/exceptions/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected internal server error occurred. Please try again later."))
                .andExpect(jsonPath("$.message").value(not(containsString("Null pointer"))))
                .andExpect(jsonPath("$.message").value(not(containsString("secret internal"))));
    }
}
