package com.rpacloud.common.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PageResponse<T> {

    private long total;
    private List<T> items;

    public static <T> PageResponse<T> of(long total, List<T> items) {
        return new PageResponse<>(total, items);
    }
}
