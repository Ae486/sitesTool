package com.rpacloud.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpacloud.auth.dto.TokenResponse;
import com.rpacloud.auth.dto.UserResponse;
import com.rpacloud.auth.service.AuthService;
import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthService authService;
    @MockBean private RpaProperties rpaProperties;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // CorsConfig requires non-null cors origins
        RpaProperties.Cors corsConfig = new RpaProperties.Cors();
        when(rpaProperties.getCors()).thenReturn(corsConfig);
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login("test@example.com", "pass"))
                .thenReturn(TokenResponse.bearer("jwt-abc"));

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test@example.com")
                        .param("password", "pass"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("jwt-abc"))
                .andExpect(jsonPath("$.token_type").value("bearer"));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        User user = User.builder().id(1L).email("test@example.com")
                .fullName("Test").isActive(true)
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0)).build();
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(authService.getCurrentUser(user))
                .thenReturn(new UserResponse(1L, "test@example.com", "Test", true,
                        LocalDateTime.of(2025, 1, 1, 0, 0)));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.full_name").value("Test"))
                .andExpect(jsonPath("$.is_active").value(true));

        SecurityContextHolder.clearContext();
    }

    @Test
    void authStatusWhenDisabled() throws Exception {
        RpaProperties.Auth authConfig = new RpaProperties.Auth();
        authConfig.setDisabled(true);
        when(rpaProperties.getAuth()).thenReturn(authConfig);

        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth_disabled").value(true))
                .andExpect(jsonPath("$.dev_user.email").value("dev@localhost"));
    }

    @Test
    void authStatusWhenEnabled() throws Exception {
        RpaProperties.Auth authConfig = new RpaProperties.Auth();
        authConfig.setDisabled(false);
        when(rpaProperties.getAuth()).thenReturn(authConfig);

        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auth_disabled").value(false));
    }
}
