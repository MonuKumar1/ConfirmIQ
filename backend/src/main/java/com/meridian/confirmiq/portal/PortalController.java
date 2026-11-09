package com.meridian.confirmiq.portal;

import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.confirmation.*;
import com.meridian.confirmiq.document.Document;
import com.meridian.confirmiq.document.DocumentController;
import com.meridian.confirmiq.document.DocumentService;
import com.meridian.confirmiq.document.Document.Source;
import com.meridian.confirmiq.inbound.InboundService;
import com.meridian.confirmiq.inbound.InboundService.InboundResult;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.trade.TradeDetail;
import com.meridian.confirmiq.trade.TradeQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Client-facing API. Every call is scoped to the counterparty of the authenticated client user. */
@Tag(name = "Client portal")
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalController {

    private final ConfirmationRepository confirmations;
    private final ConfirmationWorkflowService workflow;
    private final TradeQueryService tradeQuery;
    private final DocumentService documents;
    private final InboundService inbound;

    @GetMapping("/confirmations")
    @Transactional(readOnly = true)
    public List<PortalRow> list() {
        CurrentUser user = CurrentUser.get();
        Instant now = Instant.now();
        return confirmations.findAll((root, q, cb) -> cb.and(
                        cb.equal(root.get("trade").get("counterparty").get("id"), user.counterpartyId()),
                        root.get("status").in(ConfirmationStatus.CLIENT_VISIBLE)))
                .stream()
                .sorted(Comparator.comparing(Confirmation::getSentAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> new PortalRow(c.getTrade().getId(), c.getTrade().getTradeRef(),
                        c.getTrade().getProductType().getDisplayName(), c.getTrade().getTradeDate(),
                        c.getTrade().getValueDate(), c.getTrade().currencyPair(), c.getTrade().getSellCurrency(),
                        c.getTrade().getSellAmount(), c.getTrade().getBuyCurrency(), c.getTrade().getBuyAmount(),
                        c.getTrade().getRate(), portalStatus(c.getStatus()), c.getSentAt(), c.getSlaDueAt(),
                        c.isSlaBreached(now)))
                .toList();
    }

    @GetMapping("/confirmations/{tradeId}")
    @Transactional(readOnly = true)
    public TradeDetail get(@PathVariable Long tradeId) {
        return tradeQuery.detail(owned(tradeId), CurrentUser.get(), false);
    }

    @PostMapping("/confirmations/{tradeId}/affirm")
    public TradeDetail affirm(@PathVariable Long tradeId) {
        return act(tradeId, ConfirmationAction.AFFIRM, null);
    }

    @PostMapping("/confirmations/{tradeId}/dispute")
    public TradeDetail dispute(@PathVariable Long tradeId, @RequestBody DisputeRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("Please describe the discrepancy");
        }
        return act(tradeId, ConfirmationAction.DISPUTE, request.reason());
    }

    @PostMapping(value = "/confirmations/{tradeId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InboundResult upload(@PathVariable Long tradeId, @RequestParam("file") MultipartFile file) throws IOException {
        owned(tradeId);
        return inbound.process(file.getBytes(), file.getOriginalFilename(), Source.CLIENT_PORTAL, CurrentUser.get(), tradeId);
    }

    @GetMapping("/documents/{documentId}/content")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> document(@PathVariable Long documentId,
                                           @RequestParam(defaultValue = "false") boolean download) {
        Document d = documents.find(documentId);
        CurrentUser user = CurrentUser.get();
        boolean visible = d.getTrade() != null
                && Objects.equals(d.getTrade().getCounterparty().getId(), user.counterpartyId())
                && d.getStage() != Document.Stage.DRAFT;
        if (!visible) {
            throw ApiException.notFound("Document", documentId);
        }
        return DocumentController.pdfResponse(d, documents.content(d), download);
    }

    private TradeDetail act(Long tradeId, ConfirmationAction action, String comment) {
        Confirmation conf = owned(tradeId);
        workflow.perform(conf.getTrade().getId(), new ActionRequest(action, comment, null));
        return tradeQuery.detail(confirmations.findByTradeId(tradeId).orElseThrow(), CurrentUser.get(), false);
    }

    private Confirmation owned(Long tradeId) {
        CurrentUser user = CurrentUser.get();
        return confirmations.findByTradeId(tradeId)
                .filter(c -> Objects.equals(c.getTrade().getCounterparty().getId(), user.counterpartyId()))
                .filter(c -> ConfirmationStatus.CLIENT_VISIBLE.contains(c.getStatus()))
                .orElseThrow(() -> ApiException.notFound("Confirmation", tradeId));
    }

    /** Clients see a simplified status vocabulary. */
    private static String portalStatus(ConfirmationStatus s) {
        return switch (s) {
            case AWAITING_RESPONSE -> "ACTION_REQUIRED";
            case MATCHED -> "MATCHED";
            case MISMATCHED, DISPUTED -> "UNDER_REVIEW";
            case CONFIRMED -> "CONFIRMED";
            default -> s.name();
        };
    }

    public record PortalRow(Long tradeId, String tradeRef, String product, LocalDate tradeDate, LocalDate valueDate,
                            String currencyPair, String youBuyCurrency, BigDecimal youBuyAmount,
                            String youSellCurrency, BigDecimal youSellAmount, BigDecimal rate, String status,
                            Instant sentAt, Instant responseDueAt, boolean overdue) {}

    public record DisputeRequest(@NotBlank @Size(max = 1000) String reason) {}
}
