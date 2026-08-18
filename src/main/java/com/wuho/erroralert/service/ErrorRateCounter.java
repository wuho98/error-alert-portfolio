package com.wuho.erroralert.service;

import java.time.Instant;

/** Counts received errors in fixed one-minute windows. */
public interface ErrorRateCounter {

    long increment(long projectId, String errorCode, Instant receivedAt);
}
