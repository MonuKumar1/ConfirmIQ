package com.meridian.confirmiq.trade;

import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.confirmation.Confirmation;
import com.meridian.confirmiq.confirmation.ConfirmationAction;
import com.meridian.confirmiq.confirmation.ConfirmationRepository;
import com.meridian.confirmiq.confirmation.ConfirmationWorkflowService;
import com.meridian.confirmiq.counterparty.Counterparty;
import com.meridian.confirmiq.counterparty.CounterpartyRepository;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.trade.Trade.IngestionChannel;
import com.meridian.confirmiq.trade.TradeValidator.EconomicTerms;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Books trades from upstream systems: validation, idempotency on external reference,
 * confirmation creation and straight-through enrichment when static data is complete.
 */
@Service
@RequiredArgsConstructor
public class TradeIngestionService {

    private final TradeRepository trades;
    private final CounterpartyRepository counterparties;
    private final ConfirmationRepository confirmations;
    private final ConfirmationWorkflowService workflow;
    private final AuditService audit;
    private final Validator validator;

    @Transactional
    public IngestionResult ingest(TradeRequest req, IngestionChannel channel) {
        return ingest(req, channel, CurrentUser.getOrSystem(), Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public IngestionResult ingest(TradeRequest req, IngestionChannel channel, CurrentUser actor, Instant at) {
        Set<ConstraintViolation<TradeRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            return IngestionResult.rejected(req.externalRef(), violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().toList());
        }
        if (trades.existsByExternalRef(req.externalRef())) {
            return IngestionResult.duplicate(req.externalRef());
        }
        Counterparty cpty = counterparties.findByShortCodeIgnoreCase(req.counterparty())
                .or(() -> counterparties.findByLei(req.counterparty()))
                .orElse(null);
        List<String> errors = new ArrayList<>(TradeValidator.validate(EconomicTerms.of(req), req.productType()));
        if (cpty == null) {
            errors.addFirst("Unknown counterparty " + req.counterparty());
        }
        if (!errors.isEmpty()) {
            return IngestionResult.rejected(req.externalRef(), errors);
        }

        Trade t = new Trade();
        t.setExternalRef(req.externalRef());
        t.setSourceSystem(req.sourceSystem());
        t.setIngestionChannel(channel);
        t.setProductType(req.productType());
        t.setCounterparty(cpty);
        t.setTradeDate(req.tradeDate());
        t.setExecutionTime(req.executionTime());
        t.setValueDate(req.valueDate());
        t.setBuyCurrency(req.buyCurrency());
        t.setBuyAmount(req.buyAmount());
        t.setSellCurrency(req.sellCurrency());
        t.setSellAmount(req.sellAmount());
        t.setRate(req.rate());
        if (req.productType() == ProductType.FX_SWAP) {
            t.setFarValueDate(req.farValueDate());
            t.setFarBuyAmount(req.farBuyAmount());
            t.setFarSellAmount(req.farSellAmount());
            t.setFarRate(req.farRate());
        }
        if (req.productType() == ProductType.FX_NDF) {
            t.setSettlementCurrency(req.settlementCurrency());
            t.setFixingDate(req.fixingDate());
            t.setFixingSource(req.fixingSource());
        }
        t.setBook(req.book());
        t.setTrader(req.trader());
        t.setCreatedAt(at);
        t.setUpdatedAt(at);
        trades.save(t);
        t.setTradeRef("MC-FX-%d-%06d".formatted(req.tradeDate().getYear(), t.getId()));

        Confirmation conf = new Confirmation();
        conf.setTrade(t);
        conf.setCreatedAt(at);
        conf.setStatusChangedAt(at);
        confirmations.save(conf);
        audit.record(actor, at, t.getId(), "TRADE", t.getId(), "TRADE_CAPTURED", null, "NEW",
                "%s %s %s via %s from %s".formatted(req.productType().getDisplayName(), t.currencyPair(),
                        req.externalRef(), channel, req.sourceSystem()), null);

        List<String> issues = workflow.enrichmentIssues(t);
        String enrichment;
        if (issues.isEmpty()) {
            workflow.perform(conf, ConfirmationAction.ENRICH, CurrentUser.SYSTEM, at, null, null);
            enrichment = "Auto-enriched";
        } else {
            enrichment = "Exception: " + String.join("; ", issues);
            audit.record(CurrentUser.SYSTEM, at, t.getId(), "CONFIRMATION", conf.getId(), "ENRICHMENT_EXCEPTION",
                    null, null, String.join("; ", issues), null);
        }
        return IngestionResult.accepted(req.externalRef(), t.getId(), t.getTradeRef(), enrichment);
    }

    public record IngestionResult(String externalRef, Outcome outcome, Long tradeId, String tradeRef,
                                  String message, List<String> errors) {
        static IngestionResult accepted(String ext, Long id, String ref, String msg) {
            return new IngestionResult(ext, Outcome.ACCEPTED, id, ref, msg, List.of());
        }

        static IngestionResult duplicate(String ext) {
            return new IngestionResult(ext, Outcome.DUPLICATE, null, null,
                    "External reference already booked – ignored (idempotent)", List.of());
        }

        static IngestionResult rejected(String ext, List<String> errors) {
            return new IngestionResult(ext, Outcome.REJECTED, null, null,
                    errors.stream().collect(Collectors.joining("; ")), errors);
        }
    }

    public enum Outcome { ACCEPTED, DUPLICATE, REJECTED }
}
