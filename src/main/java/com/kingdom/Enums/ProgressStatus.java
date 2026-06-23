package com.kingdom.Enums;

/** The challenge lifecycle state machine: join -> progress -> submit -> verify -> outcome. */
public enum ProgressStatus {
    JOINED,
    IN_PROGRESS,
    SUBMITTED,
    VERIFIED,
    REJECTED,
    CANCELED
}
