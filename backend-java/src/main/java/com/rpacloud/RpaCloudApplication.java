package com.rpacloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.notification.NotificationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RpaProperties.class, NotificationProperties.class})
public class RpaCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(RpaCloudApplication.class, args);
    }
}
