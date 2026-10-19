package com.tanmaysinghx.portalsso.user.web.dto;

import java.util.List;

/** Plaintext codes, returned exactly once; only their hashes are stored. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {}
