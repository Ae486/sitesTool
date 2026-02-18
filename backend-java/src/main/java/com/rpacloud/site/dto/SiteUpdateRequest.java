package com.rpacloud.site.dto;

import java.util.List;

import lombok.Data;

@Data
public class SiteUpdateRequest {
    private String name;
    private String url;
    private String description;
    private Long categoryId;
    private List<Long> tagIds;
    private Boolean isActive;
    private Integer sortOrder;
}
