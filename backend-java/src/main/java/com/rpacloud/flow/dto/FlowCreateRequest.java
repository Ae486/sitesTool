package com.rpacloud.flow.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowCreateRequest {
    @NotNull
    private Long siteId;
    @NotBlank
    private String name;
    private String description;
    private String cronExpression;
    private Boolean isActive = true;
    @NotNull
    private Map<String, Object> dsl;
    private Boolean headless = true;
    private String browserType = "chromium";
    private String browserPath;
    private Boolean useCdpMode = false;
    private Integer cdpPort = 9222;
    private String cdpUserDataDir;
    private Boolean useProxy = false;
    private Long proxyId;
}
