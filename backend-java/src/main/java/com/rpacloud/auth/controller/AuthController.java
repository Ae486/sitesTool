package com.rpacloud.auth.controller;

import com.rpacloud.auth.dto.BootstrapRequest;
import com.rpacloud.auth.dto.TokenResponse;
import com.rpacloud.auth.dto.UserResponse;
import com.rpacloud.auth.service.AuthService;
import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RpaProperties rpaProperties;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenResponse login(@RequestParam String username, @RequestParam String password) {
        return authService.login(username, password);
    }

    @PostMapping("/bootstrap")
    public UserResponse bootstrap(@Valid @RequestBody BootstrapRequest request) {
        return authService.bootstrap(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User currentUser) {
        return authService.getCurrentUser(currentUser);
    }

    @GetMapping("/status")
    public Map<String, Object> authStatus() {
        boolean disabled = rpaProperties.getAuth().isDisabled();
        if (disabled) {
            return Map.of(
                    "auth_disabled", true,
                    "dev_user", Map.of(
                            "id", 1,
                            "email", "dev@localhost",
                            "full_name", "Dev User",
                            "is_active", true,
                            "is_superuser", true
                    )
            );
        }
        return Map.of("auth_disabled", false);
    }
}
