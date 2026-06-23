package com.kingdom.Enums;

/**
 * Why a submission was rejected:
 *  NOT_COMPLETED -> retry while the window is open
 *  UNVERIFIABLE  -> auto re-sync, else expire
 *  FLAGGED       -> impossible/cheating; no retry; sent to admin anti-cheat review
 */
public enum RejectionReason {
    NOT_COMPLETED,
    UNVERIFIABLE,
    FLAGGED,
    NUTRITION_IMAGE_NOT_MATCHED,
    NUTRITION_VALUES_NOT_VERIFIED,
    KNOWLEDGE_SCORE_TOO_LOW,
    GITHUB_REPOSITORY_INVALID
}
