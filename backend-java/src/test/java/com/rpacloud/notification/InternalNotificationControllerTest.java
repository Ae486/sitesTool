package com.rpacloud.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.service.InternalTokenProvider;
import com.rpacloud.notification.InternalNotificationController.NotificationRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalNotificationControllerTest {

    @Mock private InternalTokenProvider tokenProvider;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    private InternalNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalNotificationController(tokenProvider, notificationService, Optional.of(emailService));
    }

    @Test
    void pushNotificationSuccess() {
        Claims claims = new DefaultClaims(Map.of("sub", "exec-1", "user_id", 1L));
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(claims);

        var request = new NotificationRequest("valid-token", "websocket", "Hello", null, null);
        controller.pushNotification(request);
        verify(notificationService).push(eq(1L), anyMap());
    }

    @Test
    void pushEmailNotificationSuccess() {
        Claims claims = new DefaultClaims(Map.of("sub", "exec-1", "user_id", 1L));
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(claims);

        var request = new NotificationRequest("valid-token", "email", "Body text", "user@test.com", "Test Subject");
        controller.pushNotification(request);
        verify(emailService).send("user@test.com", "Test Subject", "Body text");
    }

    @Test
    void emailWithoutToThrows() {
        Claims claims = new DefaultClaims(Map.of("sub", "exec-1", "user_id", 1L));
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(claims);

        var request = new NotificationRequest("valid-token", "email", "Body", null, null);
        assertThatThrownBy(() -> controller.pushNotification(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("to");
    }

    @Test
    void rejectsMissingToken() {
        var request = new NotificationRequest(null, "websocket", "x", null, null);
        assertThatThrownBy(() -> controller.pushNotification(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assert biz.getErrorCode() == ErrorCode.INVALID_INTERNAL_TOKEN;
                });
    }

    @Test
    void rejectsInvalidToken() {
        when(tokenProvider.validateAndParse("bad")).thenReturn(null);

        var request = new NotificationRequest("bad", null, null, null, null);
        assertThatThrownBy(() -> controller.pushNotification(request))
                .isInstanceOf(BizException.class);
    }
}
