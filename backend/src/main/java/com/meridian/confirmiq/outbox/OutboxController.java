package com.meridian.confirmiq.outbox;

import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.common.PageResponse;
import com.meridian.confirmiq.outbox.OutboxMessage.MessageType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Outbox (simulated client communications)")
@RestController
@RequestMapping("/api/v1/outbox")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxRepository repository;

    @GetMapping
    public PageResponse<OutboxView> list(@RequestParam(required = false) MessageType type,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        Specification<OutboxMessage> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (type != null) p.add(cb.equal(root.get("messageType"), type));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("subject")), like),
                        cb.like(cb.lower(root.get("recipients")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))), OutboxView::of);
    }

    @GetMapping("/{id}")
    public OutboxView get(@PathVariable Long id) {
        return repository.findById(id).map(OutboxView::of)
                .orElseThrow(() -> ApiException.notFound("Message", id));
    }

    public record OutboxView(Long id, Long tradeId, MessageType messageType, String recipients, String cc,
                             String subject, String body, Long documentId, String status, String createdBy,
                             Instant createdAt) {
        public static OutboxView of(OutboxMessage m) {
            return new OutboxView(m.getId(), m.getTradeId(), m.getMessageType(), m.getRecipients(), m.getCc(),
                    m.getSubject(), m.getBody(), m.getDocumentId(), m.getStatus(), m.getCreatedBy(), m.getCreatedAt());
        }
    }
}
