package com.rpacloud.site.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.rpacloud.catalog.dto.CategoryResponse;
import com.rpacloud.catalog.dto.TagResponse;
import com.rpacloud.site.entity.Site;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class SiteResponse {
    private Long id;
    private String name;
    private String url;
    private String description;
    private Long categoryId;
    private List<Long> tagIds;
    private Boolean isActive;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private CategoryResponse category;
    private List<TagResponse> tags;

    public static SiteResponse from(Site site) {
        return SiteResponse.builder()
                .id(site.getId())
                .name(site.getName())
                .url(site.getUrl())
                .description(site.getDescription())
                .categoryId(site.getCategory() != null ? site.getCategory().getId() : null)
                .tagIds(site.getTags() != null ? site.getTags().stream().map(t -> t.getId()).toList() : List.of())
                .isActive(site.getIsActive())
                .sortOrder(site.getSortOrder())
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .category(site.getCategory() != null ? CategoryResponse.from(site.getCategory()) : null)
                .tags(site.getTags() != null ? site.getTags().stream().map(TagResponse::from).toList() : List.of())
                .build();
    }
}
