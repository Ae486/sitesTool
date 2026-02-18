package com.rpacloud.catalog.dto;

import com.rpacloud.catalog.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TagResponse {
    private Long id;
    private String name;
    private String color;

    public static TagResponse from(Tag t) {
        return new TagResponse(t.getId(), t.getName(), t.getColor());
    }
}
