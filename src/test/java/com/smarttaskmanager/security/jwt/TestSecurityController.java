package com.smarttaskmanager.security.jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal controller used only in integration tests to verify that the JWT
 * authentication filter and security handlers work correctly.
 *
 * <p>This controller is excluded from production by the test profile and exists
 * solely to provide a protected endpoint for security tests.</p>
 */
@RestController
@RequestMapping("/api/v1/test")
public class TestSecurityController {

    @GetMapping("/protected")
    public String protectedEndpoint() {
        return "OK";
    }
}
