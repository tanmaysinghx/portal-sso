package com.tanmaysinghx.portalsso.common.error;

public class ResourceNotFoundException extends PortalException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
