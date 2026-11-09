package com.meridian.confirmiq.inbound;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "match_result")
@Getter
@Setter
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tradeId;
    private Long inboundDocumentId;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    /** QR (our paper returned) or REFERENCE (counterparty paper quoting our reference). */
    private String identifiedBy;

    /** True when the QR fingerprint equals the current economic terms. */
    private boolean qrVerified;

    private String fieldsJson;
    private Instant createdAt = Instant.now();

    public enum Outcome { MATCHED, MISMATCHED }
}
