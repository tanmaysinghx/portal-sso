package com.tanmaysinghx.portalsso.connect.service;

import com.tanmaysinghx.portalsso.common.error.ErrorCode;
import com.tanmaysinghx.portalsso.common.error.PortalException;

/**
 * A redeem attempt with the wrong code or verifier. Its own type so the redeem transaction can
 * commit the failed-attempt counter ({@code noRollbackFor}) without also committing on any other
 * failure, such as an error while generating credentials.
 */
public class ConnectRedeemRejectedException extends PortalException {

    public ConnectRedeemRejectedException() {
        super(ErrorCode.CONNECT_REDEEM_REJECTED);
    }
}
