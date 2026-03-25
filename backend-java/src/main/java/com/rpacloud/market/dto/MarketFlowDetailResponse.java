package com.rpacloud.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MarketFlowDetailResponse {
    private Long id;
    private String title;
    private String description;
    private String authorName;
    private Integer version;
    private String visibility;
    private Integer downloadCount;
    private BigDecimal avgRating;
    private Map<String, Object> dslSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
