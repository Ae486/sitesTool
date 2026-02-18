package com.rpacloud.auth.service;

import com.rpacloud.auth.dto.BootstrapRequest;
import com.rpacloud.auth.dto.TokenResponse;
import com.rpacloud.auth.dto.UserResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenResponse login(String email, String password) {
        User user = userService.authenticate(email, password)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "Incorrect credentials"));
        String token = jwtTokenProvider.createToken(user.getId(), user.getEmail());
        return TokenResponse.bearer(token);
    }

    @Transactional
    public UserResponse bootstrap(BootstrapRequest request) {
        if (userService.count() > 0) {
            throw new BizException(ErrorCode.DUPLICATE_RESOURCE, "Admin already initialized");
        }
        User user = userService.createUser(request.getEmail(), request.getPassword(), request.getFullName());
        user.setIsSuperuser(true);
        user = userService.save(user);
        return UserResponse.from(user);
    }

    public UserResponse getCurrentUser(User user) {
        return UserResponse.from(user);
    }
}
