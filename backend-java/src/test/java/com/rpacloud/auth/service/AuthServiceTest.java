package com.rpacloud.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.rpacloud.auth.dto.BootstrapRequest;
import com.rpacloud.auth.dto.TokenResponse;
import com.rpacloud.auth.dto.UserResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @InjectMocks private AuthService authService;

    @Test
    void loginSuccess() {
        User user = User.builder().id(1L).email("test@example.com").build();
        when(userService.authenticate("test@example.com", "pass123")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createToken(1L, "test@example.com")).thenReturn("jwt-token");

        TokenResponse response = authService.login("test@example.com", "pass123");

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("bearer");
    }

    @Test
    void loginWrongPasswordThrows401() {
        when(userService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("test@example.com", "wrong"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void bootstrapCreatesFirstUser() {
        when(userService.count()).thenReturn(0L);
        User created = User.builder().id(1L).email("admin@test.com").fullName("Admin").isActive(true).build();
        when(userService.createUser("admin@test.com", "pass", "Admin")).thenReturn(created);
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BootstrapRequest req = new BootstrapRequest();
        req.setEmail("admin@test.com");
        req.setPassword("pass");
        req.setFullName("Admin");

        UserResponse resp = authService.bootstrap(req);
        assertThat(resp.getEmail()).isEqualTo("admin@test.com");
    }

    @Test
    void bootstrapRejectsIfUsersExist() {
        when(userService.count()).thenReturn(1L);

        BootstrapRequest req = new BootstrapRequest();
        req.setEmail("admin@test.com");
        req.setPassword("pass");

        assertThatThrownBy(() -> authService.bootstrap(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }
}
