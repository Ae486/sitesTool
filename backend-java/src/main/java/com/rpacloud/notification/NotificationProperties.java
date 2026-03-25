package com.rpacloud.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rpa.notification")
public class NotificationProperties {
    private boolean emailEnabled = false;
    private String emailFrom = "noreply@example.com";
}
