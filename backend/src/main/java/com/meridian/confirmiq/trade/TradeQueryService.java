package com.meridian.confirmiq.trade;

import com.meridian.confirmiq.audit.AuditController.AuditView;
import com.meridian.confirmiq.audit.AuditEventRepository;
import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.confirmation.Confirmation;
import com.meridian.confirmiq.confirmation.ConfirmationRepository;
import com.meridian.confirmiq.confirmation.ConfirmationStatus;
import com.meridian.confirmiq.confirmation.ConfirmationWorkflowService;
import com.meridian.confirmiq.counterparty.Counterparty;
import com.meridian.confirmiq.counterparty.CounterpartyDtos.SsiView;
import com.meridian.confirmiq.counterparty.SettlementInstructionRepository;
import com.meridian.confirmiq.document.DocumentRepository;
import com.meridian.confirmiq.document.DocumentView;
import com.meridian.confirmiq.inbound.InboundService;
import com.meridian.confirmiq.inbound.MatchResultRepository;
import com.meridian.confirmiq.outbox.OutboxController.OutboxView;
import com.meridian.confirmiq.outbox.OutboxRepository;
import com.meridian.confirmiq.product.ProductTermsService;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.trade.TradeDetail.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private final ConfirmationRepository confirmations;
    private final DocumentRepository documents;
    private final MatchResultRepository matches;
    private final AuditEventRepository audit;
    private final OutboxRepository outbox;
    private final SettlementInstructionRepository ssis;
    private final ProductTermsService terms;
    private final ConfirmationWorkflowService workflow;
    private final InboundService inbound;
    private final TradeRepository trades;

    @Transactional(readOnly = true)
    public TradeDetail detail(Long tradeId) {
        Confirmation conf = confirmations.findByTradeId(tradeId)
                .orElseThrow(() -> ApiException.notFound("Trade", tradeId));
        return detail(conf, CurrentUser.get(), true);
    }

    /** @param internal include internal-only data (audit, messages, enrichment, allowed ops actions) */
    @Transactional(readOnly = true)
    public TradeDetail detail(Confirmation conf, CurrentUser user, boolean internal) {
        Trade t = conf.getTrade();
        Counterparty c = t.getCounterparty();
        Instant now = Instant.now();
        var cptyInfo = new CounterpartyInfo(c.getId(), c.getShortCode(), c.getLegalName(), c.getLei(),
                c.getDeliveryChannel(), c.getConfirmationSlaHours(), c.getMasterAgreement());
        var confInfo = new ConfirmationInfo(conf.getStatus(), conf.getStatus().getDescription(), conf.getPreparedBy(),
                conf.getApprovedBy(), conf.getSentAt(), conf.getSlaDueAt(), conf.isSlaBreached(now),
                conf.getChaserCount(), conf.getLastChasedAt(), conf.isEscalated(), conf.getDisputeReason(),
                conf.getMatchedAt(), conf.getConfirmedAt(), conf.getStatusChangedAt());

        var docs = documents.findByTradeIdOrderByCreatedAtDescIdDesc(t.getId()).stream()
                .filter(d -> internal || d.getStage() != com.meridian.confirmiq.document.Document.Stage.DRAFT)
                .map(DocumentView::of).toList();
        var matchViews = matches.findByTradeIdOrderByCreatedAtDescIdDesc(t.getId()).stream()
                .map(m -> new MatchView(m.getId(), m.getInboundDocumentId(), m.getOutcome().name(), m.getIdentifiedBy(),
                        m.isQrVerified(), m.getCreatedAt(), inbound.readFields(m)))
                .toList();

        return new TradeDetail(t.getId(), t.getTradeRef(), t.getExternalRef(), t.getSourceSystem(),
                t.getIngestionChannel(), t.getProductType(), t.getProductType().getDisplayName(), t.getTradeDate(),
                t.getExecutionTime(), t.getValueDate(), t.currencyPair(), t.getBuyCurrency(), t.getBuyAmount(),
                t.getSellCurrency(), t.getSellAmount(), t.getRate(), t.getFarValueDate(), t.getFarBuyAmount(),
                t.getFarSellAmount(), t.getFarRate(), t.getSettlementCurrency(), t.getFixingDate(), t.getFixingSource(),
                internal ? t.getBook() : null, internal ? t.getTrader() : null, t.getEconomicVersion(), t.getCreatedAt(),
                cptyInfo, confInfo, terms.termsOf(t), settlement(t), docs, matchViews,
                internal ? audit.findByTradeIdOrderByOccurredAtAscIdAsc(t.getId()).stream().map(AuditView::of).toList() : List.of(),
                internal ? outbox.findByTradeIdOrderByCreatedAtDesc(t.getId()).stream().map(OutboxView::of).toList() : List.of(),
                workflow.allowedActions(conf, user),
                internal && conf.getStatus() == ConfirmationStatus.NEW ? workflow.enrichmentIssues(t) : List.of());
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return trades.search(q.trim(), PageRequest.of(0, 8)).stream()
                .map(t -> new SearchHit(t.getId(), t.getTradeRef(), t.getExternalRef(), t.getCounterparty().getLegalName(),
                        t.getProductType().getDisplayName(), t.currencyPair()))
                .toList();
    }

    private List<SsiPair> settlement(Trade t) {
        Set<String> ccys = new LinkedHashSet<>();
        if (t.getProductType() == ProductType.FX_NDF) {
            ccys.add(t.getSettlementCurrency());
        } else {
            ccys.add(t.getBuyCurrency());
            ccys.add(t.getSellCurrency());
        }
        return ccys.stream().map(ccy -> new SsiPair(ccy,
                ssis.findHouseInstruction(ccy).map(SsiView::of).orElse(null),
                ssis.findFirstByCounterpartyIdAndCurrencyAndActiveTrueOrderByEffectiveFromDesc(t.getCounterparty().getId(), ccy)
                        .map(SsiView::of).orElse(null)))
                .toList();
    }

    public record SearchHit(Long tradeId, String tradeRef, String externalRef, String counterparty, String product,
                            String currencyPair) {}
}
