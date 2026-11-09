package com.meridian.confirmiq.trade;

import com.meridian.confirmiq.confirmation.ActionRequest;
import com.meridian.confirmiq.confirmation.ConfirmationWorkflowService;
import com.meridian.confirmiq.document.DocumentRepository;
import com.meridian.confirmiq.document.DocumentView;
import com.meridian.confirmiq.trade.Trade.IngestionChannel;
import com.meridian.confirmiq.trade.TradeIngestionService.IngestionResult;
import com.meridian.confirmiq.trade.TradeIngestionService.Outcome;
import com.meridian.confirmiq.trade.TradeQueryService.SearchHit;
import com.meridian.confirmiq.trade.TradeXmlParser.XmlRejectedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Trades")
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeIngestionService ingestion;
    private final TradeXmlParser xmlParser;
    private final TradeQueryService query;
    private final ConfirmationWorkflowService workflow;
    private final DocumentRepository documents;

    @Operation(summary = "Book a trade from an upstream system (idempotent on externalRef)")
    @PostMapping
    @PreAuthorize("hasAnyRole('MAKER','SUPERVISOR')")
    public ResponseEntity<IngestionResult> create(@RequestBody TradeRequest request) {
        IngestionResult result = ingestion.ingest(request, IngestionChannel.API);
        HttpStatus status = switch (result.outcome()) {
            case ACCEPTED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.OK;
            case REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(result);
    }

    @Operation(summary = "Upload one or more tradeBooking XML files (validated against trade-booking-v1.xsd)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MAKER','SUPERVISOR')")
    public List<FileResult> upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        List<FileResult> out = new ArrayList<>();
        for (MultipartFile f : files) {
            try {
                List<IngestionResult> results = xmlParser.parse(f.getBytes()).stream()
                        .map(r -> ingestion.ingest(r, IngestionChannel.XML_UPLOAD)).toList();
                out.add(new FileResult(f.getOriginalFilename(), null, results,
                        results.stream().filter(r -> r.outcome() == Outcome.ACCEPTED).count()));
            } catch (XmlRejectedException e) {
                out.add(new FileResult(f.getOriginalFilename(), e.getMessage(), List.of(), 0));
            }
        }
        return out;
    }

    @GetMapping("/{id}")
    public TradeDetail get(@PathVariable Long id) {
        return query.detail(id);
    }

    @Operation(summary = "Perform a confirmation lifecycle action (enrich, generate, approve, send, amend, ...)")
    @PostMapping("/{id}/actions")
    public TradeDetail act(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
        workflow.perform(id, request);
        return query.detail(id);
    }

    @GetMapping("/{id}/documents")
    @Transactional(readOnly = true)
    public List<DocumentView> documents(@PathVariable Long id) {
        return documents.findByTradeIdOrderByCreatedAtDescIdDesc(id).stream().map(DocumentView::of).toList();
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam String q) {
        return query.search(q);
    }

    public record FileResult(String fileName, String error, List<IngestionResult> trades, long accepted) {}
}
