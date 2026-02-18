package com.rpacloud.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagCreateRequest {
    @NotBlank
    private String name;
    private String color;
}
