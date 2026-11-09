package com.meridian.confirmiq.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A client communication. Delivery is simulated: messages are stored and viewable, never sent over SMTP. */
@Entity
@Table(name = "outbox_message")
@Getter
@Setter
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tradeId;

    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    private String recipients;
    private String cc;
    private String subject;
    private String body;
    private Long documentId;
    private String status = "DELIVERED";
    private String createdBy;
    private Instant createdAt = Instant.now();

    public enum MessageType { CONFIRMATION, AMENDED_CONFIRMATION, CANCELLATION, CHASER, ESCALATION, PORTAL_NOTIFICATION }
}
