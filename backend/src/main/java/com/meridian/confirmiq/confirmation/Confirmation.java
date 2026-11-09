package com.meridian.confirmiq.confirmation;

import com.meridian.confirmiq.trade.Trade;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "confirmation")
@Getter
@Setter
public class Confirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @Enumerated(EnumType.STRING)
    private ConfirmationStatus status = ConfirmationStatus.NEW;

    private String preparedBy;
    private String approvedBy;
    private Instant sentAt;
    private Instant slaDueAt;
    private Instant matchedAt;
    private Instant confirmedAt;
    private int chaserCount;
    private Instant lastChasedAt;
    private boolean escalated;
    private String disputeReason;
    private Instant statusChangedAt = Instant.now();
    private Instant createdAt = Instant.now();

    @Version
    private long lockVersion;

    public boolean isSlaBreached(Instant now) {
        return slaDueAt != null && !status.isTerminal() && status != ConfirmationStatus.MATCHED && now.isAfter(slaDueAt);
    }
}
