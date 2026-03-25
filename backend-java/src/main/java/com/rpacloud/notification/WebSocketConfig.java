package com.rpacloud.notification;

import com.rpacloud.common.config.RpaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final RpaProperties rpaProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String origins = rpaProperties.getCors().getOrigins();
        String[] allowedOrigins = (origins == null || origins.isBlank() || "*".equals(origins.trim()))
                ? new String[]{"*"}
                : origins.split(",");
        registry.addHandler(notificationHandler, "/ws/notifications")
                .setAllowedOrigins(allowedOrigins);
    }
}
