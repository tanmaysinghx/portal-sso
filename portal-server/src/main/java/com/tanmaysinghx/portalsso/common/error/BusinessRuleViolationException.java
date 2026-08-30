package com.tanmaysinghx.portalsso.common.error;

public class BusinessRuleViolationException extends PortalException {

    public BusinessRuleViolationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleViolationException(String message) {
        super(ErrorCode.MALFORMED_REQUEST, message);
    }
}
