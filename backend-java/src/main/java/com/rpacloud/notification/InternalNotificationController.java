package com.rpacloud.notification;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.service.InternalTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final InternalTokenProvider tokenProvider;
    private final NotificationService notificationService;
    private final Optional<EmailService> emailService;

    @PostMapping("/notification")
    public Map<String, String> pushNotification(@RequestBody NotificationRequest request) {
        if (request.internalToken() == null) {
            throw new BizException(ErrorCode.INVALID_INTERNAL_TOKEN, "Missing internal_token");
        }

        Claims claims = tokenProvider.validateAndParse(request.internalToken());
        if (claims == null) {
            throw new BizException(ErrorCode.INVALID_INTERNAL_TOKEN, "Invalid internal token");
        }

        Long userId = extractUserId(claims.get("user_id"));
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_INTERNAL_TOKEN, "Missing user_id in token");
        }

        String channel = request.channel() != null ? request.channel() : "websocket";
        String message = request.message() != null ? request.message() : "";

        if ("websocket".equals(channel)) {
            notificationService.push(userId, Map.of(
                    "type", "notification",
                    "message", message
            ));
        } else if ("email".equals(channel)) {
            if (emailService.isEmpty()) {
                log.warn("Email notification skipped: email service not enabled");
                return Map.of("status", "skipped", "reason", "email not enabled");
            }
            String to = request.to();
            String subject = request.subject() != null ? request.subject() : "RPA Notification";
            if (to == null || to.isBlank()) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "Email 'to' address is required");
            }
            emailService.get().send(to, subject, message);
        } else {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Unknown notification channel: " + channel);
        }

        return Map.of("status", "sent");
    }

    private static Long extractUserId(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public record NotificationRequest(
            @JsonProperty("internal_token") String internalToken,
            String channel,
            String message,
            String to,
            String subject
    ) {}
}
