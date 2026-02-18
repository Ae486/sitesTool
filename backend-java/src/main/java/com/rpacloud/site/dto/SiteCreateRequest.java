package com.rpacloud.site.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SiteCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String url;
    private String description;
    private Long categoryId;
    private List<Long> tagIds;
    private Boolean isActive = true;
    private Integer sortOrder = 0;
}
