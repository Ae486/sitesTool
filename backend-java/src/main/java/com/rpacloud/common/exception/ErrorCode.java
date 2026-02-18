package com.rpacloud.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND"),
    DUPLICATE_RESOURCE(409, "DUPLICATE_RESOURCE"),
    FLOW_ALREADY_RUNNING(409, "FLOW_ALREADY_RUNNING"),
    VALIDATION_FAILED(400, "VALIDATION_FAILED"),
    UNAUTHORIZED(401, "UNAUTHORIZED"),
    FORBIDDEN(403, "FORBIDDEN"),
    RATE_LIMITED(429, "RATE_LIMITED"),
    INSUFFICIENT_BALANCE(402, "INSUFFICIENT_BALANCE"),
    LLM_SERVICE_ERROR(502, "LLM_SERVICE_ERROR"),
    LLM_QUOTA_EXCEEDED(429, "LLM_QUOTA_EXCEEDED"),
    INVALID_INTERNAL_TOKEN(401, "INVALID_INTERNAL_TOKEN"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR");

    private final int httpStatus;
    private final String code;

    ErrorCode(int httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
