package com.meridian.confirmiq.audit;

import com.meridian.confirmiq.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Audit trail")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository repository;

    @GetMapping
    public PageResponse<AuditView> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long tradeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (actor != null && !actor.isBlank()) p.add(cb.equal(cb.lower(root.get("actor")), actor.toLowerCase()));
            if (action != null && !action.isBlank()) p.add(cb.equal(root.get("action"), action));
            if (entityType != null && !entityType.isBlank()) p.add(cb.equal(root.get("entityType"), entityType));
            if (tradeId != null) p.add(cb.equal(root.get("tradeId"), tradeId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (to != null) p.add(cb.lessThan(root.get("occurredAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var result = repository.findAll(spec, PageRequest.of(page, Math.min(size, 500),
                Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
        return PageResponse.of(result, AuditView::of);
    }

    public record AuditView(Long id, Long tradeId, String entityType, Long entityId, String action,
                            String fromStatus, String toStatus, String actor, String actorRole,
                            String details, String comment, Instant occurredAt) {
        public static AuditView of(AuditEvent e) {
            return new AuditView(e.getId(), e.getTradeId(), e.getEntityType(), e.getEntityId(), e.getAction(),
                    e.getFromStatus(), e.getToStatus(), e.getActor(), e.getActorRole(), e.getDetails(),
                    e.getComment(), e.getOccurredAt());
        }
    }
}
