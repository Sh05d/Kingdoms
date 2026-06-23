package com.kingdom.Enums;

/** State of a lobby invite. EXPIRED = no response before the invite/lobby deadline. */
public enum InviteStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED
}
