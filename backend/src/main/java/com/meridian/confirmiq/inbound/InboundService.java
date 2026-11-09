package com.meridian.confirmiq.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.confirmation.Confirmation;
import com.meridian.confirmiq.confirmation.ConfirmationRepository;
import com.meridian.confirmiq.confirmation.ConfirmationStatus;
import com.meridian.confirmiq.confirmation.ConfirmationWorkflowService;
import com.meridian.confirmiq.document.Document;
import com.meridian.confirmiq.document.Document.ProcessingStatus;
import com.meridian.confirmiq.document.Document.Source;
import com.meridian.confirmiq.document.DocumentService;
import com.meridian.confirmiq.document.QrCodes;
import com.meridian.confirmiq.inbound.MatchingEngine.FieldResult;
import com.meridian.confirmiq.inbound.MatchingEngine.FieldStatus;
import com.meridian.confirmiq.product.ProductTermsService;
import com.meridian.confirmiq.product.TermField;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.security.Role;
import com.meridian.confirmiq.trade.Trade;
import com.meridian.confirmiq.trade.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Processes documents returned by clients: identify the trade (QR first, then printed reference),
 * extract the terms, match them against our booking and move the confirmation to MATCHED or MISMATCHED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundService {

    private final DocumentService documentService;
    private final TradeRepository trades;
    private final ConfirmationRepository confirmations;
    private final ConfirmationWorkflowService workflow;
    private final ProductTermsService terms;
    private final PdfTermExtractor extractor;
    private final MatchingEngine engine;
    private final MatchResultRepository matchResults;
    private final AuditService audit;
    private final ObjectMapper json;

    @Transactional
    public InboundResult process(byte[] content, String fileName, Source source, CurrentUser actor, Long contextTradeId) {
        return process(content, fileName, source, actor, contextTradeId, Instant.now());
    }

    @Transactional
    public InboundResult process(byte[] content, String fileName, Source source, CurrentUser actor,
                                 Long contextTradeId, Instant at) {
        if (content.length < 5 || !new String(content, 0, 5).startsWith("%PDF")) {
            throw ApiException.badRequest(fileName + " is not a PDF document");
        }
        Document doc = documentService.storeInbound(content, fileName, source, actor.username(), at);

        String text;
        try {
            text = extractor.text(content);
        } catch (IllegalArgumentException e) {
            return finish(doc, ProcessingStatus.FAILED, "Unreadable PDF: " + e.getMessage(), null, actor, at);
        }
        Optional<QrCodes.Payload> qr = QrCodes.readFromPdf(content);
        String tradeRef = qr.map(QrCodes.Payload::tradeRef).or(() -> extractor.findTradeRef(text)).orElse(null);
        String identifiedBy = qr.isPresent() ? "QR" : "REFERENCE";

        Trade trade = tradeRef == null ? null : trades.findByTradeRef(tradeRef).orElse(null);
        if (contextTradeId != null) {
            if (trade != null && !trade.getId().equals(contextTradeId)) {
                return finish(doc, ProcessingStatus.NOT_EXPECTED,
                        "Document refers to " + tradeRef + ", not the selected trade", null, actor, at);
            }
            if (trade == null) {
                trade = trades.findById(contextTradeId).orElseThrow(() -> ApiException.notFound("Trade", contextTradeId));
                identifiedBy = "UPLOAD_CONTEXT";
            }
        }
        if (trade == null) {
            return finish(doc, ProcessingStatus.UNIDENTIFIED, tradeRef == null
                    ? "No QR code or Meridian trade reference found – link manually"
                    : "Reference " + tradeRef + " is not a known trade", null, actor, at);
        }
        if (actor.role() == Role.CLIENT && !Objects.equals(actor.counterpartyId(), trade.getCounterparty().getId())) {
            throw ApiException.notFound("Trade", trade.getId());
        }
        doc.setTrade(trade);
        return match(doc, trade, text, qr.orElse(null), identifiedBy, actor, at);
    }

    /** Manually links an unidentified document to a trade and runs matching. */
    @Transactional
    public InboundResult link(Long documentId, Long tradeId) {
        Document doc = documentService.find(documentId);
        if (doc.getDirection() != Document.Direction.INBOUND || doc.getProcessingStatus() == ProcessingStatus.MATCHED
                || doc.getProcessingStatus() == ProcessingStatus.MISMATCHED) {
            throw ApiException.conflict("Only unprocessed inbound documents can be linked");
        }
        Trade trade = trades.findById(tradeId).orElseThrow(() -> ApiException.notFound("Trade", tradeId));
        doc.setTrade(trade);
        byte[] content = documentService.content(doc);
        CurrentUser actor = CurrentUser.get();
        audit.record(trade.getId(), "DOCUMENT", doc.getId(), "INBOUND_LINKED", null, null,
                doc.getFileName() + " linked manually", null);
        return match(doc, trade, extractor.text(content), QrCodes.readFromPdf(content).orElse(null), "MANUAL_LINK",
                actor, Instant.now());
    }

    private InboundResult match(Document doc, Trade trade, String text, QrCodes.Payload qr, String identifiedBy,
                                CurrentUser actor, Instant at) {
        Confirmation conf = confirmations.findByTradeId(trade.getId()).orElseThrow();
        audit.record(actor, at, trade.getId(), "DOCUMENT", doc.getId(), "INBOUND_RECEIVED", null, null,
                "%s via %s (identified by %s)".formatted(doc.getFileName(), doc.getSource(), identifiedBy), null);
        if (!ConfirmationStatus.MATCHABLE.contains(conf.getStatus())) {
            return finish(doc, ProcessingStatus.NOT_EXPECTED,
                    "Received while confirmation is " + conf.getStatus().getDescription() + "; filed without matching",
                    trade, actor, at);
        }

        List<TermField> expected = terms.termsOf(trade);
        PdfTermExtractor.Extraction extraction = extractor.extract(text, expected);
        List<FieldResult> fields = new ArrayList<>(engine.compare(expected, extraction.values()));

        String currentHash = terms.termsHash(trade);
        boolean qrVerified = qr != null && currentHash.startsWith(qr.termsHashPrefix());
        if (qr != null && !qrVerified) {
            fields.add(new FieldResult("documentVersion", "Document version", "v" + trade.getEconomicVersion(),
                    "v" + qr.economicVersion(), FieldStatus.MISMATCH, "Client returned superseded paper"));
        }
        boolean matched = MatchingEngine.allMatch(fields);

        MatchResult result = new MatchResult();
        result.setTradeId(trade.getId());
        result.setInboundDocumentId(doc.getId());
        result.setOutcome(matched ? MatchResult.Outcome.MATCHED : MatchResult.Outcome.MISMATCHED);
        result.setIdentifiedBy(identifiedBy);
        result.setQrVerified(qrVerified);
        result.setFieldsJson(toJson(fields));
        result.setCreatedAt(at);
        matchResults.save(result);

        long ok = fields.stream().filter(f -> f.status() == FieldStatus.MATCH).count();
        String summary = matched
                ? "All %d terms matched%s".formatted(fields.size(), qrVerified ? " · QR fingerprint verified" : "")
                : "%d/%d terms matched · breaks: %s".formatted(ok, fields.size(), fields.stream()
                .filter(f -> f.status() != FieldStatus.MATCH).map(FieldResult::label).collect(Collectors.joining(", ")));
        workflow.applyMatchOutcome(conf, matched, summary, actor, at);
        return finish(doc, matched ? ProcessingStatus.MATCHED : ProcessingStatus.MISMATCHED, summary, trade, actor, at);
    }

    private InboundResult finish(Document doc, ProcessingStatus status, String note, Trade trade, CurrentUser actor, Instant at) {
        doc.setProcessingStatus(status);
        doc.setProcessingNote(note);
        if (trade == null) {
            audit.record(actor, at, null, "DOCUMENT", doc.getId(), "INBOUND_" + status.name(), null, null,
                    doc.getFileName() + ": " + note, null);
        }
        log.info("Inbound {} -> {} ({})", doc.getFileName(), status, note);
        return new InboundResult(doc.getId(), doc.getFileName(), status,
                trade == null ? null : trade.getId(), trade == null ? null : trade.getTradeRef(), note);
    }

    public List<FieldResult> readFields(MatchResult r) {
        try {
            return List.of(json.readValue(r.getFieldsJson(), FieldResult[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(List<FieldResult> fields) {
        try {
            return json.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public record InboundResult(Long documentId, String fileName, ProcessingStatus status, Long tradeId,
                                String tradeRef, String note) {}
}
