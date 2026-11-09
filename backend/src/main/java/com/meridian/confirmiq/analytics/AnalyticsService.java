package com.meridian.confirmiq.analytics;

import com.meridian.confirmiq.confirmation.Confirmation;
import com.meridian.confirmiq.confirmation.ConfirmationRepository;
import com.meridian.confirmiq.confirmation.ConfirmationStatus;
import com.meridian.confirmiq.inbound.MatchResult;
import com.meridian.confirmiq.inbound.MatchResultRepository;
import com.meridian.confirmiq.trade.ProductType;
import com.meridian.confirmiq.trade.Trade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.meridian.confirmiq.confirmation.ConfirmationStatus.*;

/** Operational KPIs computed over the confirmation book (in memory – the book is small). */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ConfirmationRepository confirmations;
    private final MatchResultRepository matchResults;

    @Transactional(readOnly = true)
    public Dashboard dashboard(int days) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Confirmation> all = confirmations.findAllWithTrade();
        List<Confirmation> open = all.stream().filter(c -> !c.getStatus().isTerminal()).toList();

        long awaiting = count(all, AWAITING_RESPONSE);
        long breaks = all.stream().filter(c -> BREAKS.contains(c.getStatus())).count();
        long pendingApproval = count(all, PENDING_APPROVAL);
        long slaBreached = open.stream().filter(c -> c.isSlaBreached(now)).count();
        long confirmedToday = all.stream().filter(c -> c.getConfirmedAt() != null
                && c.getConfirmedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(today)).count();
        long tradesToday = all.stream().filter(c -> c.getTrade().getTradeDate().equals(today)).count();

        // Match rate: share of trades whose first inbound document matched without a break.
        List<MatchResult> allMatches = matchResults.findAll();
        Map<Long, MatchResult> firstMatch = new HashMap<>();
        allMatches.stream().sorted(Comparator.comparing(MatchResult::getCreatedAt))
                .forEach(m -> firstMatch.putIfAbsent(m.getTradeId(), m));
        double firstTimeMatchRate = firstMatch.isEmpty() ? 0 : 100.0 * firstMatch.values().stream()
                .filter(m -> m.getOutcome() == MatchResult.Outcome.MATCHED).count() / firstMatch.size();

        List<Confirmation> confirmed = all.stream()
                .filter(c -> c.getStatus() == CONFIRMED && c.getSentAt() != null && c.getConfirmedAt() != null).toList();
        double avgHoursToConfirm = confirmed.stream()
                .mapToLong(c -> Duration.between(c.getSentAt(), c.getConfirmedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        long sentTotal = all.stream().filter(c -> c.getSentAt() != null).count();
        long confirmedWithinSla = confirmed.stream()
                .filter(c -> c.getSlaDueAt() == null || !c.getConfirmedAt().isAfter(c.getSlaDueAt())).count();
        double slaCompliance = confirmed.isEmpty() ? 0 : 100.0 * confirmedWithinSla / confirmed.size();
        long stp = all.stream().filter(c -> c.getStatus() != NEW).count();
        double enrichmentStpRate = all.isEmpty() ? 0 : 100.0 * stp / all.size();

        Kpis kpis = new Kpis(all.size(), open.size(), tradesToday, awaiting, breaks, pendingApproval, slaBreached,
                confirmedToday, round(firstTimeMatchRate), round(avgHoursToConfirm), round(slaCompliance),
                round(enrichmentStpRate), sentTotal);

        Map<ConfirmationStatus, Long> byStatus = new EnumMap<>(ConfirmationStatus.class);
        for (ConfirmationStatus s : ConfirmationStatus.values()) byStatus.put(s, count(all, s));

        // Ageing of open confirmations by business days since trade date.
        String[] buckets = {"T+0", "T+1", "T+2", "T+3–5", "> T+5"};
        Map<String, Long> ageing = new LinkedHashMap<>();
        for (String b : buckets) ageing.put(b, 0L);
        for (Confirmation c : open) {
            long d = ChronoUnit.DAYS.between(c.getTrade().getTradeDate(), today);
            String b = d <= 0 ? "T+0" : d == 1 ? "T+1" : d == 2 ? "T+2" : d <= 5 ? "T+3–5" : "> T+5";
            ageing.merge(b, 1L, Long::sum);
        }

        List<DailyPoint> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long booked = all.stream().filter(c -> c.getTrade().getTradeDate().equals(d)).count();
            long done = all.stream().filter(c -> c.getConfirmedAt() != null
                    && c.getConfirmedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(d)).count();
            long brk = allMatches.stream().filter(m -> m.getOutcome() == MatchResult.Outcome.MISMATCHED
                    && m.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().equals(d)).count();
            trend.add(new DailyPoint(d, booked, done, brk));
        }

        List<CounterpartyStat> byCounterparty = open.stream()
                .collect(Collectors.groupingBy(c -> c.getTrade().getCounterparty().getLegalName()))
                .entrySet().stream()
                .map(e -> new CounterpartyStat(e.getKey(), e.getValue().size(),
                        e.getValue().stream().filter(c -> BREAKS.contains(c.getStatus())).count(),
                        e.getValue().stream().filter(c -> c.isSlaBreached(now)).count()))
                .sorted(Comparator.comparingLong(CounterpartyStat::open).reversed())
                .limit(8).toList();

        Map<ProductType, Long> byProduct = new EnumMap<>(ProductType.class);
        for (ProductType p : ProductType.values()) byProduct.put(p, 0L);
        all.forEach(c -> byProduct.merge(c.getTrade().getProductType(), 1L, Long::sum));

        List<Attention> attention = open.stream()
                .filter(c -> c.isSlaBreached(now) || BREAKS.contains(c.getStatus()) || c.isEscalated())
                .sorted(Comparator.comparing((Confirmation c) -> c.getSlaDueAt() == null ? Instant.MAX : c.getSlaDueAt()))
                .limit(10)
                .map(c -> {
                    Trade t = c.getTrade();
                    String reason = c.isEscalated() ? "Escalated" : BREAKS.contains(c.getStatus())
                            ? c.getStatus().getDescription() : "SLA breached";
                    return new Attention(t.getId(), t.getTradeRef(), t.getCounterparty().getLegalName(),
                            t.getProductType().getDisplayName(), c.getStatus(), reason, c.getSlaDueAt(), c.getChaserCount());
                })
                .toList();

        return new Dashboard(kpis, byStatus, ageing, trend, byCounterparty, byProduct, attention, now);
    }

    private static long count(List<Confirmation> list, ConfirmationStatus s) {
        return list.stream().filter(c -> c.getStatus() == s).count();
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    public record Kpis(long totalTrades, long openConfirmations, long tradesToday, long awaitingClient, long breaks,
                       long pendingApproval, long slaBreached, long confirmedToday, double firstTimeMatchRate,
                       double avgHoursToConfirm, double slaCompliance, double enrichmentStpRate, long sentTotal) {}

    public record DailyPoint(LocalDate date, long booked, long confirmed, long breaks) {}

    public record CounterpartyStat(String counterparty, long open, long breaks, long slaBreached) {}

    public record Attention(Long tradeId, String tradeRef, String counterparty, String product,
                            ConfirmationStatus status, String reason, Instant slaDueAt, int chaserCount) {}

    public record Dashboard(Kpis kpis, Map<ConfirmationStatus, Long> byStatus, Map<String, Long> ageing,
                            List<DailyPoint> trend, List<CounterpartyStat> byCounterparty,
                            Map<ProductType, Long> byProduct, List<Attention> attention, Instant asOf) {}
}
