package com.rpacloud.common.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class OffsetBasedPageRequest extends PageRequest {

    private final long offset;

    public OffsetBasedPageRequest(int offset, int limit) {
        super(offset / Math.max(limit, 1), Math.max(limit, 1), Sort.unsorted());
        this.offset = Math.max(offset, 0);
    }

    public OffsetBasedPageRequest(int offset, int limit, Sort sort) {
        super(offset / Math.max(limit, 1), Math.max(limit, 1), sort);
        this.offset = Math.max(offset, 0);
    }

    @Override
    public long getOffset() {
        return offset;
    }
}
