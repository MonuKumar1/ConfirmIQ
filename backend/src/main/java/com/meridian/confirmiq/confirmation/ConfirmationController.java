package com.meridian.confirmiq.confirmation;

import com.meridian.confirmiq.common.PageResponse;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.BlotterFilter;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.BlotterRow;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.Queue;
import com.meridian.confirmiq.trade.ProductType;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Tag(name = "Confirmation blotter")
@RestController
@RequestMapping("/api/v1/confirmations")
@RequiredArgsConstructor
public class ConfirmationController {

    private final ConfirmationQueryService query;

    @GetMapping
    public PageResponse<BlotterRow> blotter(
            @RequestParam(defaultValue = "ALL") Queue queue,
            @RequestParam(required = false) ConfirmationStatus status,
            @RequestParam(required = false) Long counterpartyId,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateTo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        return query.search(new BlotterFilter(queue, status, counterpartyId, productType, tradeDateFrom, tradeDateTo, q),
                page, size, sort);
    }

    @GetMapping("/queues")
    public Map<Queue, Long> queues() {
        return query.queueCounts();
    }
}
