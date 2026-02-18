package com.rpacloud.flow.dto;

import java.util.Map;

import lombok.Data;

@Data
public class FlowUpdateRequest {
    private String name;
    private String description;
    private String cronExpression;
    private Boolean isActive;
    private Map<String, Object> dsl;
    private Boolean headless;
    private String browserType;
    private String browserPath;
    private Boolean useCdpMode;
    private Integer cdpPort;
    private String cdpUserDataDir;
    private Boolean useProxy;
    private Long proxyId;
}
