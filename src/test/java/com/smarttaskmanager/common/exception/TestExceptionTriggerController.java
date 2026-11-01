package com.smarttaskmanager.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only REST controller to reliably trigger various exception conditions.
 */
@RestController
@RequestMapping("/test/exceptions")
public class TestExceptionTriggerController {

    public record TestValidationDto(
            @NotBlank(message = "Title is required")
            @Size(min = 3, max = 50, message = "Title must be between 3 and 50 characters")
            String title,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password
    ) {}

    @PostMapping("/validation")
    public String triggerValidation(@Valid @RequestBody TestValidationDto dto) {
        return "OK";
    }

    @GetMapping("/not-found")
    public void triggerNotFound() {
        throw new ResourceNotFoundException("Task", "id", 999L);
    }

    @GetMapping("/duplicate")
    public void triggerDuplicate() {
        throw new DuplicateResourceException("Category", "name", "Work");
    }

    @GetMapping("/business-rule")
    public void triggerBusinessRule() {
        throw new BusinessRuleException("Cannot complete an already cancelled task");
    }

    @GetMapping("/unauthorized-access")
    public void triggerUnauthorizedAccess() {
        throw new UnauthorizedAccessException("You do not own this resource");
    }

    @GetMapping("/invalid-token")
    public void triggerInvalidToken() {
        throw new InvalidTokenException("Verification token has expired", "TOKEN_EXPIRED");
    }

    @GetMapping("/rate-limit")
    public void triggerRateLimit() {
        throw new RateLimitExceededException("Too many login attempts. Please wait.");
    }

    @GetMapping("/type-mismatch/{id}")
    public Long triggerTypeMismatch(@PathVariable("id") Long id) {
        return id;
    }

    @GetMapping("/missing-param")
    public String triggerMissingParam(@RequestParam("requiredParam") String param) {
        return param;
    }

    @GetMapping("/db-conflict")
    public void triggerDbConflict() {
        throw new DataIntegrityViolationException("Unique constraint violation: table=users column=email");
    }

    @GetMapping("/access-denied")
    public void triggerAccessDenied() {
        throw new AccessDeniedException("Access denied by security rule");
    }

    @GetMapping("/internal-error")
    public void triggerInternalError() {
        throw new RuntimeException("Null pointer in secret internal business engine");
    }
}
