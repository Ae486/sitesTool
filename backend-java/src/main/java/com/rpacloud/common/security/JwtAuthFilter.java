package com.rpacloud.common.security;

import java.io.IOException;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RpaProperties rpaProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (rpaProperties.getAuth().isDisabled()) {
            setDevUser();
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token != null && jwtTokenProvider.validate(token)) {
            String email = jwtTokenProvider.getEmail(token);
            userRepository.findByEmail(email).filter(User::getIsActive).ifPresent(user -> {
                var auth = new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
                MDC.put("userId", String.valueOf(user.getId()));
            });
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }

    private void setDevUser() {
        User devUser = User.builder()
                .id(1L)
                .email("dev@localhost")
                .fullName("Dev User")
                .isActive(true)
                .isSuperuser(true)
                .hashedPassword("not-used")
                .build();
        var auth = new UsernamePasswordAuthenticationToken(devUser, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
