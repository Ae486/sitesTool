package com.rpacloud.flow.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.rpacloud.flow.entity.FlowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FlowResponse {
    private Long id;
    private Long siteId;
    private String name;
    private String description;
    private String cronExpression;
    private Boolean isActive;
    private Map<String, Object> dsl;
    private FlowStatus lastStatus;
    private Boolean headless;
    private String browserType;
    private String browserPath;
    private Boolean useCdpMode;
    private Integer cdpPort;
    private String cdpUserDataDir;
    private Boolean useProxy;
    private Long proxyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
