package com.meridian.confirmiq.document;

import com.meridian.confirmiq.trade.Trade;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Metadata of a stored document. Content lives in {@link DocumentStorage}, addressed by storageKey. */
@Entity
@Table(name = "document")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocType docType;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    private int version;
    private String fileName;
    private String storageKey;
    private String sha256;
    private long sizeBytes;
    private String mimeType = "application/pdf";
    private String termsHash;
    private Long templateVersionId;

    @Enumerated(EnumType.STRING)
    private Source source;

    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus;

    private String processingNote;
    private String createdBy;
    private Instant createdAt = Instant.now();

    public enum DocType { CONFIRMATION, AMENDED_CONFIRMATION, CANCELLATION, CLIENT_RESPONSE, REPORT }

    public enum Direction { OUTBOUND, INBOUND }

    public enum Stage { DRAFT, FINAL, RECEIVED }

    public enum Source { GENERATED, OPS_UPLOAD, CLIENT_PORTAL, INBOX, SIMULATOR }

    public enum ProcessingStatus { MATCHED, MISMATCHED, UNIDENTIFIED, NOT_EXPECTED, FAILED }
}
