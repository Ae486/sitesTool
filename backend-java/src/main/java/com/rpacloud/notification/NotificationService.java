package com.rpacloud.notification;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationWebSocketHandler webSocketHandler;

    public void push(Long userId, Map<String, Object> event) {
        webSocketHandler.pushToUser(userId, event);
    }

    public void pushExecutionComplete(Long userId, Long flowId, String status, String message) {
        push(userId, Map.of(
                "type", "execution_complete",
                "flow_id", flowId,
                "status", status,
                "message", message != null ? message : ""
        ));
    }
}
