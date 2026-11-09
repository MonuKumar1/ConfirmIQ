package com.meridian.confirmiq.document;

import com.meridian.confirmiq.document.Document.*;
import com.meridian.confirmiq.trade.Trade;

import java.time.Instant;

public record DocumentView(Long id, Long tradeId, String tradeRef, String counterparty, DocType docType,
                           Direction direction, Stage stage, int version, String fileName, String sha256,
                           long sizeBytes, Source source, ProcessingStatus processingStatus, String processingNote,
                           String termsHash, String createdBy, Instant createdAt) {

    public static DocumentView of(Document d) {
        Trade t = d.getTrade();
        return new DocumentView(d.getId(), t == null ? null : t.getId(), t == null ? null : t.getTradeRef(),
                t == null ? null : t.getCounterparty().getLegalName(), d.getDocType(), d.getDirection(), d.getStage(),
                d.getVersion(), d.getFileName(), d.getSha256(), d.getSizeBytes(), d.getSource(),
                d.getProcessingStatus(), d.getProcessingNote(), d.getTermsHash(), d.getCreatedBy(), d.getCreatedAt());
    }
}
