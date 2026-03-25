package com.rpacloud.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MarketPublishRequest {
    @NotNull
    private Long flowId;
    @NotBlank
    private String title;
    private String description;
    @Pattern(regexp = "PUBLIC|LINK_ONLY", message = "visibility must be PUBLIC or LINK_ONLY")
    private String visibility = "PUBLIC";
}
