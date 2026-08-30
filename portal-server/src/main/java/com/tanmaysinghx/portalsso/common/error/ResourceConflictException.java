package com.tanmaysinghx.portalsso.common.error;

public class ResourceConflictException extends PortalException {

    public ResourceConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }

    public ResourceConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceConflictException(ErrorCode errorCode) {
        super(errorCode);
    }
}
