package com.meridian.confirmiq.confirmation;

import org.junit.jupiter.api.Test;

import static com.meridian.confirmiq.confirmation.ConfirmationStatus.*;
import static com.meridian.confirmiq.security.Role.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationActionTest {

    @Test
    void onlyCheckersAndSupervisorsApprove() {
        assertThat(ConfirmationAction.APPROVE.isAllowed(PENDING_APPROVAL, CHECKER)).isTrue();
        assertThat(ConfirmationAction.APPROVE.isAllowed(PENDING_APPROVAL, MAKER)).isFalse();
        assertThat(ConfirmationAction.APPROVE.isAllowed(DRAFTED, CHECKER)).isFalse();
    }

    @Test
    void nothingHappensAfterTerminalStates() {
        for (ConfirmationAction a : ConfirmationAction.values()) {
            assertThat(a.getFrom()).doesNotContain(CONFIRMED, CANCELLED);
        }
    }

    @Test
    void clientsCanOnlyAffirmOrDisputeReleasedConfirmations() {
        for (ConfirmationAction a : ConfirmationAction.values()) {
            if (a.getRoles().contains(CLIENT)) {
                assertThat(a).isIn(ConfirmationAction.AFFIRM, ConfirmationAction.DISPUTE);
                assertThat(CLIENT_VISIBLE).containsAll(a.getFrom());
            }
        }
    }

    @Test
    void sensitiveActionsRequireAComment() {
        assertThat(ConfirmationAction.REJECT.isCommentRequired()).isTrue();
        assertThat(ConfirmationAction.CANCEL.isCommentRequired()).isTrue();
        assertThat(ConfirmationAction.AMEND.isCommentRequired()).isTrue();
        assertThat(ConfirmationAction.FORCE_MATCH.isCommentRequired()).isTrue();
    }
}
