package com.tanmaysinghx.portalsso.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationErrorDetail(
        String field,
        String message,
        Object rejectedValue
) {}
