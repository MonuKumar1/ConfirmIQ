package com.meridian.confirmiq.confirmation;

import com.meridian.confirmiq.security.Role;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

import static com.meridian.confirmiq.confirmation.ConfirmationStatus.*;
import static com.meridian.confirmiq.security.Role.*;

/**
 * The confirmation state machine. Each user action declares the states it may be taken from,
 * the roles allowed to take it, and whether a comment is mandatory. Target states are resolved
 * by {@link ConfirmationWorkflowService} because some actions (e.g. CHASE) keep the current state.
 */
@Getter
public enum ConfirmationAction {
    ENRICH("Enrich", EnumSet.of(NEW), EnumSet.of(MAKER, SUPERVISOR), false),
    GENERATE("Generate draft", EnumSet.of(ENRICHED, DRAFTED), EnumSet.of(MAKER, SUPERVISOR), false),
    SUBMIT("Submit for approval", EnumSet.of(DRAFTED), EnumSet.of(MAKER, SUPERVISOR), false),
    APPROVE("Approve", EnumSet.of(PENDING_APPROVAL), EnumSet.of(CHECKER, SUPERVISOR), false),
    REJECT("Reject", EnumSet.of(PENDING_APPROVAL), EnumSet.of(CHECKER, SUPERVISOR), true),
    SEND("Send to client", EnumSet.of(APPROVED), EnumSet.of(MAKER, SUPERVISOR), false),
    CHASE("Chase client", EnumSet.of(AWAITING_RESPONSE, MISMATCHED, DISPUTED), EnumSet.of(MAKER, SUPERVISOR), false),
    CONFIRM("Mark confirmed", EnumSet.of(MATCHED), EnumSet.of(MAKER, CHECKER, SUPERVISOR), false),
    FORCE_MATCH("Accept break", EnumSet.of(MISMATCHED), EnumSet.of(SUPERVISOR), true),
    DISPUTE("Raise dispute", EnumSet.of(AWAITING_RESPONSE, MISMATCHED), EnumSet.of(MAKER, SUPERVISOR, CLIENT), true),
    AFFIRM("Affirm", EnumSet.of(AWAITING_RESPONSE, MATCHED), EnumSet.of(CLIENT), false),
    AMEND("Amend economics", EnumSet.of(ENRICHED, DRAFTED, AWAITING_RESPONSE, MISMATCHED, DISPUTED),
            EnumSet.of(MAKER, SUPERVISOR), true),
    CANCEL("Cancel", EnumSet.complementOf(EnumSet.of(CONFIRMED, CANCELLED)), EnumSet.of(SUPERVISOR), true);

    private final String label;
    private final Set<ConfirmationStatus> from;
    private final Set<Role> roles;
    private final boolean commentRequired;

    ConfirmationAction(String label, Set<ConfirmationStatus> from, Set<Role> roles, boolean commentRequired) {
        this.label = label;
        this.from = from;
        this.roles = roles;
        this.commentRequired = commentRequired;
    }

    public boolean isAllowed(ConfirmationStatus status, Role role) {
        return from.contains(status) && roles.contains(role);
    }
}
