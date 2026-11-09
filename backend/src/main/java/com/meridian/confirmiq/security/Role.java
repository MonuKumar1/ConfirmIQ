package com.meridian.confirmiq.security;

public enum Role {
    /** Operations analyst: prepares, sends and chases confirmations. */
    MAKER,
    /** Independent reviewer: approves outbound documents (four-eyes). */
    CHECKER,
    /** Operations lead: static data, templates, cancellations, escalations. */
    SUPERVISOR,
    /** Counterparty user of the client portal. */
    CLIENT
}
