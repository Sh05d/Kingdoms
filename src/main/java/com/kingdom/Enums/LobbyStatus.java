package com.kingdom.Enums;

/** Lobby lifecycle. Auto-starts at startsAt; cannot be started manually. */
public enum LobbyStatus {
    OPEN,
    ACTIVE,
    FINISHED,
    CANCELLED,
    EXPIRED
}
