package com.meridian.confirmiq.confirmation;

import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Sends automatic reminders for unanswered confirmations and escalates persistent non-response. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChaserService {

    private final ConfirmationRepository confirmations;
    private final ConfirmationWorkflowService workflow;
    private final AppProperties props;

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.chaser.poll-interval-ms}")
    @Transactional
    public void chaseOutstanding() {
        Instant now = Instant.now();
        AppProperties.Chaser cfg = props.chaser();
        int chased = 0;
        for (Confirmation c : confirmations.findByStatus(ConfirmationStatus.AWAITING_RESPONSE)) {
            Instant reference = c.getLastChasedAt() != null ? c.getLastChasedAt() : c.getSentAt();
            Duration wait = Duration.ofHours(c.getChaserCount() == 0 ? cfg.firstChaseAfterHours() : cfg.intervalHours());
            if (reference != null && now.isAfter(reference.plus(wait)) && c.getChaserCount() < cfg.escalateAfter()) {
                workflow.chase(c, CurrentUser.SYSTEM, now, true);
                chased++;
            }
        }
        if (chased > 0) {
            log.info("Automatic chasers sent: {}", chased);
        }
    }
}
