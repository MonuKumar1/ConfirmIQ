package com.meridian.confirmiq.confirmation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum ConfirmationStatus {
    NEW("Booked – awaiting enrichment"),
    ENRICHED("Enriched – ready to draft"),
    DRAFTED("Draft generated"),
    PENDING_APPROVAL("Pending four-eyes approval"),
    APPROVED("Approved – ready to send"),
    AWAITING_RESPONSE("Sent – awaiting client"),
    MATCHED("Matched"),
    MISMATCHED("Mismatch – break"),
    DISPUTED("Disputed by client"),
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled");

    private final String description;

    public static final Set<ConfirmationStatus> TERMINAL = EnumSet.of(CONFIRMED, CANCELLED);
    public static final Set<ConfirmationStatus> BREAKS = EnumSet.of(MISMATCHED, DISPUTED);
    /** States after the confirmation has been released to the client. */
    public static final Set<ConfirmationStatus> CLIENT_VISIBLE =
            EnumSet.of(AWAITING_RESPONSE, MATCHED, MISMATCHED, DISPUTED, CONFIRMED);
    /** States in which an inbound client document is expected and can be matched. */
    public static final Set<ConfirmationStatus> MATCHABLE = EnumSet.of(AWAITING_RESPONSE, MISMATCHED, DISPUTED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
