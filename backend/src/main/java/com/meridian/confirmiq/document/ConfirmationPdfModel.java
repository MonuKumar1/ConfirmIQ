package com.meridian.confirmiq.document;

import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.document.Document.DocType;
import com.meridian.confirmiq.document.Document.Stage;
import com.meridian.confirmiq.product.TermField;

import java.time.Instant;
import java.util.List;

/** Everything the renderer needs; assembled by {@link DocumentService}. */
public record ConfirmationPdfModel(
        DocType docType,
        Stage stage,
        int documentVersion,
        int economicVersion,
        String tradeRef,
        String title,
        String preamble,
        List<String> clauses,
        String closing,
        AppProperties.Bank bank,
        String counterpartyName,
        String counterpartyLei,
        String attentionName,
        String attentionEmail,
        List<TermField> terms,
        List<SsiLine> settlement,
        String qrPayload,
        String fingerprint,
        String preparedBy,
        String approvedBy,
        Instant issuedAt) {

    public record SsiLine(String flow, String beneficiary, String bank, String bic, String account, String intermediary) {}
}
