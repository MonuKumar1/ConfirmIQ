package com.meridian.confirmiq.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Append-only audit record. Never updated or deleted by the application. */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tradeId;
    private String entityType;
    private Long entityId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String actor;
    private String actorRole;
    private String details;
    private String comment;
    private Instant occurredAt = Instant.now();
}
