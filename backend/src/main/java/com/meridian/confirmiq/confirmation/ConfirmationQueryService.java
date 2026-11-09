package com.meridian.confirmiq.confirmation;

import com.meridian.confirmiq.common.PageResponse;
import com.meridian.confirmiq.trade.ProductType;
import com.meridian.confirmiq.trade.Trade;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.meridian.confirmiq.confirmation.ConfirmationStatus.*;

/** Read side of the confirmation blotter: work queues, filters and queue counts. */
@Service
@RequiredArgsConstructor
public class ConfirmationQueryService {

    private final ConfirmationRepository confirmations;

    public enum Queue {
        ALL, ACTION_REQUIRED, PENDING_APPROVAL, AWAITING_CLIENT, BREAKS, SLA_BREACHED, ESCALATED, CONFIRMED, CANCELLED
    }

    private static final Set<ConfirmationStatus> ACTION_REQUIRED_STATUSES =
            EnumSet.of(NEW, ENRICHED, DRAFTED, APPROVED, MATCHED);

    @Transactional(readOnly = true)
    public PageResponse<BlotterRow> search(BlotterFilter f, int page, int size, String sort) {
        Sort order = parseSort(sort);
        var result = confirmations.findAll(spec(f), PageRequest.of(page, Math.min(size, 500), order));
        Instant now = Instant.now();
        return PageResponse.of(result, c -> BlotterRow.of(c, now));
    }

    @Transactional(readOnly = true)
    public List<BlotterRow> all(BlotterFilter f) {
        Instant now = Instant.now();
        return confirmations.findAll(spec(f), parseSort(null)).stream().map(c -> BlotterRow.of(c, now)).toList();
    }

    @Transactional(readOnly = true)
    public Map<Queue, Long> queueCounts() {
        Map<Queue, Long> counts = new EnumMap<>(Queue.class);
        for (Queue q : Queue.values()) {
            counts.put(q, confirmations.count(spec(new BlotterFilter(q, null, null, null, null, null, null))));
        }
        return counts;
    }

    private Specification<Confirmation> spec(BlotterFilter f) {
        return (root, query, cb) -> {
            Join<Confirmation, Trade> trade;
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                @SuppressWarnings("unchecked")
                Fetch<Confirmation, Trade> fetch = root.fetch("trade", JoinType.INNER);
                fetch.fetch("counterparty", JoinType.INNER);
                @SuppressWarnings("unchecked")
                Join<Confirmation, Trade> asJoin = (Join<Confirmation, Trade>) fetch;
                trade = asJoin;
            } else {
                trade = root.join("trade", JoinType.INNER);
            }
            List<Predicate> p = new ArrayList<>();
            Instant now = Instant.now();
            switch (f.queue() == null ? Queue.ALL : f.queue()) {
                case ACTION_REQUIRED -> p.add(root.get("status").in(ACTION_REQUIRED_STATUSES));
                case PENDING_APPROVAL -> p.add(cb.equal(root.get("status"), PENDING_APPROVAL));
                case AWAITING_CLIENT -> p.add(cb.equal(root.get("status"), AWAITING_RESPONSE));
                case BREAKS -> p.add(root.get("status").in(BREAKS));
                case SLA_BREACHED -> {
                    p.add(root.get("status").in(AWAITING_RESPONSE, MISMATCHED, DISPUTED));
                    p.add(cb.lessThan(root.get("slaDueAt"), now));
                }
                case ESCALATED -> {
                    p.add(cb.isTrue(root.get("escalated")));
                    p.add(cb.not(root.get("status").in(TERMINAL)));
                }
                case CONFIRMED -> p.add(cb.equal(root.get("status"), CONFIRMED));
                case CANCELLED -> p.add(cb.equal(root.get("status"), CANCELLED));
                case ALL -> { }
            }
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.counterpartyId() != null) p.add(cb.equal(trade.get("counterparty").get("id"), f.counterpartyId()));
            if (f.productType() != null) p.add(cb.equal(trade.get("productType"), f.productType()));
            if (f.tradeDateFrom() != null) p.add(cb.greaterThanOrEqualTo(trade.get("tradeDate"), f.tradeDateFrom()));
            if (f.tradeDateTo() != null) p.add(cb.lessThanOrEqualTo(trade.get("tradeDate"), f.tradeDateTo()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(trade.get("tradeRef")), like),
                        cb.like(cb.lower(trade.get("externalRef")), like),
                        cb.like(cb.lower(trade.get("counterparty").get("legalName")), like),
                        cb.like(cb.lower(trade.get("counterparty").get("shortCode")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "trade.tradeDate", "id");
        }
        String[] parts = sort.split(",");
        String field = switch (parts[0]) {
            case "tradeRef", "tradeDate", "valueDate", "productType", "buyAmount" -> "trade." + parts[0];
            case "counterparty" -> "trade.counterparty.legalName";
            case "status", "slaDueAt", "sentAt", "statusChangedAt", "chaserCount" -> parts[0];
            default -> "id";
        };
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    public record BlotterFilter(Queue queue, ConfirmationStatus status, Long counterpartyId, ProductType productType,
                                LocalDate tradeDateFrom, LocalDate tradeDateTo, String q) {}

    public record BlotterRow(Long tradeId, String tradeRef, String externalRef, ProductType productType,
                             String counterparty, String counterpartyCode, Long counterpartyId,
                             LocalDate tradeDate, LocalDate valueDate, String currencyPair,
                             String buyCurrency, BigDecimal buyAmount, String sellCurrency, BigDecimal sellAmount,
                             BigDecimal rate, ConfirmationStatus status, String preparedBy, String approvedBy,
                             Instant sentAt, Instant slaDueAt, boolean slaBreached, int chaserCount,
                             boolean escalated, int economicVersion, Instant statusChangedAt, long ageDays) {
        static BlotterRow of(Confirmation c, Instant now) {
            Trade t = c.getTrade();
            long age = java.time.temporal.ChronoUnit.DAYS.between(t.getTradeDate(), LocalDate.now());
            return new BlotterRow(t.getId(), t.getTradeRef(), t.getExternalRef(), t.getProductType(),
                    t.getCounterparty().getLegalName(), t.getCounterparty().getShortCode(), t.getCounterparty().getId(),
                    t.getTradeDate(), t.getValueDate(), t.currencyPair(), t.getBuyCurrency(), t.getBuyAmount(),
                    t.getSellCurrency(), t.getSellAmount(), t.getRate(), c.getStatus(), c.getPreparedBy(),
                    c.getApprovedBy(), c.getSentAt(), c.getSlaDueAt(), c.isSlaBreached(now), c.getChaserCount(),
                    c.isEscalated(), t.getEconomicVersion(), c.getStatusChangedAt(), Math.max(age, 0));
        }
    }
}
