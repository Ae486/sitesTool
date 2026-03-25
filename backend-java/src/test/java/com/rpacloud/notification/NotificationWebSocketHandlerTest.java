package com.rpacloud.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketHandlerTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private WebSocketSession session;

    private NotificationWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NotificationWebSocketHandler(jwtTokenProvider, new ObjectMapper());
    }

    @Test
    void connectionEstablishedWithValidToken() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notifications?token=valid"));
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());
        when(session.getId()).thenReturn("sess-1");
        when(jwtTokenProvider.validate("valid")).thenReturn(true);
        Claims claims = new DefaultClaims(Map.of("user_id", 1L));
        when(jwtTokenProvider.parseToken("valid")).thenReturn(claims);

        handler.afterConnectionEstablished(session);

        assertThat(handler.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void connectionRejectedWithInvalidToken() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notifications?token=bad"));
        when(jwtTokenProvider.validate("bad")).thenReturn(false);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void connectionRejectedWithNoToken() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notifications"));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void pushToUserSendsMessage() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notifications?token=t"));
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        when(jwtTokenProvider.validate("t")).thenReturn(true);
        Claims claims = new DefaultClaims(Map.of("user_id", 42L));
        when(jwtTokenProvider.parseToken("t")).thenReturn(claims);

        handler.afterConnectionEstablished(session);
        handler.pushToUser(42L, Map.of("type", "test"));

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void afterConnectionClosedCleansUp() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/ws/notifications?token=t"));
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");
        when(jwtTokenProvider.validate("t")).thenReturn(true);
        Claims claims = new DefaultClaims(Map.of("user_id", 1L));
        when(jwtTokenProvider.parseToken("t")).thenReturn(claims);

        handler.afterConnectionEstablished(session);
        assertThat(handler.getActiveSessionCount()).isEqualTo(1);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertThat(handler.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void pingRespondsWithPong() throws Exception {
        handler.handleMessage(session, new TextMessage("ping"));
        verify(session).sendMessage(new TextMessage("pong"));
    }
}
