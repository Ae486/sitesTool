package com.rpacloud.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final RpaProperties rpaProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (rpaProperties.getCors() == null) return;
        String raw = rpaProperties.getCors().getOrigins();
        if (raw == null || raw.isBlank()) return;
        String[] origins = raw.split(",");
        var mapping = registry.addMapping("/**")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
        if (origins.length == 1 && "*".equals(origins[0].trim())) {
            mapping.allowedOriginPatterns("*");
        } else {
            mapping.allowedOrigins(origins);
        }
    }
}
