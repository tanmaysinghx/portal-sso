package com.tanmaysinghx.portalsso.connect.entity;

/**
 * Lifecycle of an app connection request.
 *
 * <pre>
 * PENDING ──approve──► APPROVED ──redeem──► REDEEMED
 *    │                    │
 *    └──deny──► DENIED    └──(too many wrong verifiers)──► LOCKED
 * </pre>
 *
 * Expiry is not a stored state: a row past {@code expires_at} is treated as expired whatever its
 * status, and removed by the purge job.
 */
public enum AppConnectStatus {
    PENDING,
    APPROVED,
    DENIED,
    REDEEMED,
    LOCKED
}
