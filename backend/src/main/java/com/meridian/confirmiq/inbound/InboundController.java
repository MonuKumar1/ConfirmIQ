package com.meridian.confirmiq.inbound;

import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.document.Document;
import com.meridian.confirmiq.document.Document.Direction;
import com.meridian.confirmiq.document.Document.ProcessingStatus;
import com.meridian.confirmiq.document.Document.Source;
import com.meridian.confirmiq.document.DocumentRepository;
import com.meridian.confirmiq.inbound.CounterpartyPaperSimulator.Break;
import com.meridian.confirmiq.inbound.InboundService.InboundResult;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.trade.Trade;
import com.meridian.confirmiq.trade.TradeRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Inbound client documents")
@RestController
@RequestMapping("/api/v1/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inbound;
    private final DocumentRepository documents;
    private final TradeRepository trades;
    private final CounterpartyPaperSimulator simulator;
    private final InboxWatcher inboxWatcher;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<InboundResult> upload(@RequestParam("files") List<MultipartFile> files,
                                      @RequestParam(required = false) Long tradeId) throws IOException {
        CurrentUser actor = CurrentUser.get();
        List<InboundResult> results = new ArrayList<>();
        for (MultipartFile f : files) {
            results.add(inbound.process(f.getBytes(), f.getOriginalFilename(), Source.OPS_UPLOAD, actor, tradeId));
        }
        return results;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<InboundView> list(@RequestParam(required = false) ProcessingStatus status) {
        return documents.findAll((root, q, cb) -> {
                    var p = cb.equal(root.get("direction"), Direction.INBOUND);
                    return status == null ? p : cb.and(p, cb.equal(root.get("processingStatus"), status));
                }, PageRequest.of(0, 300, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(InboundView::of).getContent();
    }

    @PostMapping("/{documentId}/link")
    public InboundResult link(@PathVariable Long documentId, @Valid @RequestBody LinkRequest request) {
        return inbound.link(documentId, request.tradeId());
    }

    /** Demo tool: generates the counterparty's own confirmation, optionally with a deliberate break. */
    @PostMapping("/simulate")
    @Transactional
    public ResponseEntity<?> simulate(@Valid @RequestBody SimulateRequest request) {
        Trade trade = trades.findById(request.tradeId())
                .orElseThrow(() -> ApiException.notFound("Trade", request.tradeId()));
        Break b = request.breakField() == null ? Break.NONE : request.breakField();
        byte[] pdf = simulator.render(trade, b);
        String fileName = trade.getCounterparty().getShortCode() + "_confirmation_" + trade.getTradeRef() + ".pdf";
        if (request.deliver()) {
            return ResponseEntity.ok(inbound.process(pdf, fileName, Source.SIMULATOR, CurrentUser.get(), null));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(pdf);
    }

    @GetMapping("/inbox")
    public InboxInfo inbox() {
        return new InboxInfo(inboxWatcher.directory().toString());
    }

    public record LinkRequest(@NotNull Long tradeId) {}

    public record SimulateRequest(@NotNull Long tradeId, Break breakField, boolean deliver) {}

    public record InboxInfo(String directory) {}

    public record InboundView(Long id, String fileName, Source source, ProcessingStatus status, String note,
                              Long tradeId, String tradeRef, String counterparty, long sizeBytes, String sha256,
                              String receivedBy, Instant receivedAt) {
        static InboundView of(Document d) {
            Trade t = d.getTrade();
            return new InboundView(d.getId(), d.getFileName(), d.getSource(), d.getProcessingStatus(),
                    d.getProcessingNote(), t == null ? null : t.getId(), t == null ? null : t.getTradeRef(),
                    t == null ? null : t.getCounterparty().getLegalName(), d.getSizeBytes(), d.getSha256(),
                    d.getCreatedBy(), d.getCreatedAt());
        }
    }
}
