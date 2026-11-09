package com.meridian.confirmiq.template;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable text content of a template. Edits create a new version; exactly one version is active.
 * Text supports placeholders such as {{counterpartyName}}, {{bankName}}, {{tradeRef}}, {{productName}},
 * {{tradeDate}}, {{masterAgreement}} and {{operationsEmail}}. Clauses are separated by blank lines.
 */
@Entity
@Table(name = "template_version")
@Getter
@Setter
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private ConfirmationTemplate template;

    private int version;
    private String title;
    private String preamble;
    private String clauses;
    private String closing;
    private String changeNote;
    private boolean active;
    private String createdBy;
    private Instant createdAt = Instant.now();
}
