package com.meridian.confirmiq.confirmation;

import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.counterparty.Counterparty;
import com.meridian.confirmiq.counterparty.Counterparty.DeliveryChannel;
import com.meridian.confirmiq.counterparty.CounterpartyContact;
import com.meridian.confirmiq.counterparty.SettlementInstructionRepository;
import com.meridian.confirmiq.document.Document;
import com.meridian.confirmiq.document.Document.DocType;
import com.meridian.confirmiq.document.Document.Direction;
import com.meridian.confirmiq.document.Document.Stage;
import com.meridian.confirmiq.document.DocumentRepository;
import com.meridian.confirmiq.document.DocumentService;
import com.meridian.confirmiq.outbox.OutboxMessage.MessageType;
import com.meridian.confirmiq.outbox.OutboxService;
import com.meridian.confirmiq.product.ProductTermsService;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.security.Role;
import com.meridian.confirmiq.trade.ProductType;
import com.meridian.confirmiq.trade.Trade;
import com.meridian.confirmiq.trade.TradeValidator;
import com.meridian.confirmiq.trade.TradeValidator.EconomicTerms;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.meridian.confirmiq.confirmation.ConfirmationStatus.*;

/** Executes confirmation lifecycle actions: guards, side effects (documents, messages) and audit. */
@Service
@RequiredArgsConstructor
public class ConfirmationWorkflowService {

    private final ConfirmationRepository confirmations;
    private final DocumentService documentService;
    private final DocumentRepository documents;
    private final SettlementInstructionRepository ssis;
    private final ProductTermsService terms;
    private final OutboxService outbox;
    private final AuditService audit;
    private final AppProperties props;

    @Transactional
    public Confirmation perform(Long tradeId, ActionRequest request) {
        Confirmation conf = confirmations.findByTradeId(tradeId)
                .orElseThrow(() -> ApiException.notFound("Trade", tradeId));
        return perform(conf, request.action(), CurrentUser.get(), Instant.now(), request.comment(), request.amendment());
    }

    /** Core entry point; actor and time are explicit so history can be replayed (demo seeding). */
    @Transactional
    public Confirmation perform(Confirmation conf, ConfirmationAction action, CurrentUser actor, Instant at,
                                String comment, AmendRequest amendment) {
        guard(conf, action, actor, comment);
        Trade trade = conf.getTrade();
        switch (action) {
            case ENRICH -> {
                List<String> issues = enrichmentIssues(trade);
                if (!issues.isEmpty()) {
                    throw ApiException.conflict("Enrichment failed: " + String.join("; ", issues));
                }
                transition(conf, ENRICHED, action, actor, at, comment, "Static data and SSIs validated");
            }
            case GENERATE -> {
                conf.setPreparedBy(actor.username());
                conf.setApprovedBy(null);
                Document d = documentService.generateOutbound(conf, outboundType(trade), Stage.DRAFT, at, actor);
                transition(conf, DRAFTED, action, actor, at, comment, "Draft " + d.getFileName());
            }
            case SUBMIT -> {
                Document draft = latestOutbound(trade, Stage.DRAFT);
                if (draft == null || !terms.termsHash(trade).equals(draft.getTermsHash())) {
                    throw ApiException.conflict("Generate a draft for the current trade economics before submitting");
                }
                conf.setPreparedBy(actor.username());
                transition(conf, PENDING_APPROVAL, action, actor, at, comment, "Submitted " + draft.getFileName());
            }
            case APPROVE -> {
                conf.setApprovedBy(actor.username());
                Document d = documentService.generateOutbound(conf, outboundType(trade), Stage.FINAL, at, actor);
                transition(conf, APPROVED, action, actor, at, comment, "Final document " + d.getFileName());
            }
            case REJECT -> transition(conf, DRAFTED, action, actor, at, comment, "Returned to maker");
            case SEND -> send(conf, actor, at, comment);
            case CHASE -> chase(conf, actor, at, false);
            case CONFIRM -> {
                conf.setConfirmedAt(at);
                transition(conf, CONFIRMED, action, actor, at, comment, "Confirmation complete");
            }
            case FORCE_MATCH -> {
                conf.setMatchedAt(at);
                transition(conf, MATCHED, action, actor, at, comment, "Break accepted by supervisor");
            }
            case DISPUTE -> {
                conf.setDisputeReason(comment);
                transition(conf, DISPUTED, action, actor, at, comment,
                        actor.role() == Role.CLIENT ? "Dispute raised via client portal" : "Dispute recorded by operations");
            }
            case AFFIRM -> {
                conf.setConfirmedAt(at);
                if (conf.getMatchedAt() == null) {
                    conf.setMatchedAt(at);
                }
                transition(conf, CONFIRMED, action, actor, at, comment, "Affirmed by client via portal");
            }
            case AMEND -> amend(conf, amendment, actor, at, comment);
            case CANCEL -> cancel(conf, actor, at, comment);
        }
        return conf;
    }

    /** Actions the user may take now, including disabled ones with the reason (for the UI action bar). */
    public List<AllowedAction> allowedActions(Confirmation conf, CurrentUser user) {
        List<AllowedAction> result = new ArrayList<>();
        for (ConfirmationAction a : ConfirmationAction.values()) {
            if (!a.isAllowed(conf.getStatus(), user.role()) || a == ConfirmationAction.AFFIRM && user.role() != Role.CLIENT) {
                continue;
            }
            String reason = null;
            if (a == ConfirmationAction.APPROVE && user.username().equals(conf.getPreparedBy())) {
                reason = "Four-eyes principle: you prepared this confirmation";
            }
            result.add(new AllowedAction(a, a.getLabel(), a.isCommentRequired(), reason == null, reason));
        }
        return result;
    }

    public List<String> enrichmentIssues(Trade trade) {
        List<String> issues = new ArrayList<>();
        Counterparty c = trade.getCounterparty();
        if (!c.isActive()) {
            issues.add("Counterparty " + c.getShortCode() + " is inactive");
        }
        if (c.getDeliveryChannel() == DeliveryChannel.EMAIL && confirmationRecipients(c).isEmpty()) {
            issues.add("No confirmation contact on file for " + c.getShortCode());
        }
        List<String> currencies = trade.getProductType() == ProductType.FX_NDF
                ? List.of(trade.getSettlementCurrency())
                : List.of(trade.getBuyCurrency(), trade.getSellCurrency());
        for (String ccy : currencies) {
            if (ssis.findFirstByCounterpartyIdAndCurrencyAndActiveTrueOrderByEffectiveFromDesc(c.getId(), ccy).isEmpty()) {
                issues.add("Missing " + ccy + " SSI for " + c.getShortCode());
            }
            if (ssis.findHouseInstruction(ccy).isEmpty()) {
                issues.add("Missing house " + ccy + " SSI");
            }
        }
        return issues;
    }

    /** System transition after an inbound document has been matched. */
    @Transactional
    public void applyMatchOutcome(Confirmation conf, boolean matched, String details, CurrentUser actor, Instant at) {
        if (matched) {
            conf.setMatchedAt(at);
            conf.setDisputeReason(null);
            transition(conf, MATCHED, null, actor, at, null, details);
        } else {
            transition(conf, MISMATCHED, null, actor, at, null, details);
        }
    }

    public void chase(Confirmation conf, CurrentUser actor, Instant at, boolean automatic) {
        Trade trade = conf.getTrade();
        Counterparty c = trade.getCounterparty();
        conf.setChaserCount(conf.getChaserCount() + 1);
        conf.setLastChasedAt(at);
        int n = conf.getChaserCount();
        String subject = "%s: %s %s – %s (chaser %d)".formatted(
                n >= 3 ? "URGENT" : "Reminder", trade.getProductType().getDisplayName(), trade.getTradeRef(),
                conf.getStatus() == DISPUTED ? "dispute resolution required" : "confirmation outstanding", n);
        String body = """
                Dear %s team,

                Our records show that the confirmation below remains outstanding:

                  Reference:   %s
                  Trade date:  %s
                  Product:     %s %s
                  Sent:        %s

                Please review and return the signed confirmation, or affirm it in the Meridian Client Portal. \
                If you disagree with any terms, reply with the details so we can resolve the break promptly.

                This is chaser %d. Outstanding confirmations are escalated after %d reminders.

                Kind regards,
                FX Confirmations · %s
                """.formatted(c.getLegalName(), trade.getTradeRef(), trade.getTradeDate(), trade.currencyPair(),
                trade.getProductType().getDisplayName(), conf.getSentAt(), n, props.chaser().escalateAfter(),
                props.bank().name());
        outbox.queue(trade.getId(), MessageType.CHASER, recipientsOf(c), null, subject, body, null, actor.username(), at);
        audit.record(actor, at, trade.getId(), "CONFIRMATION", conf.getId(), "CHASER_SENT", conf.getStatus().name(),
                conf.getStatus().name(), (automatic ? "Automatic" : "Manual") + " chaser #" + n, null);

        if (n >= props.chaser().escalateAfter() && !conf.isEscalated()) {
            conf.setEscalated(true);
            outbox.queue(trade.getId(), MessageType.ESCALATION, props.bank().operationsEmail(), null,
                    "Escalation: %s unconfirmed after %d chasers".formatted(trade.getTradeRef(), n),
                    "Confirmation %s with %s remains unconfirmed after %d chasers. Supervisor follow-up with the "
                            .formatted(trade.getTradeRef(), c.getLegalName(), n)
                            + "client relationship manager is required.",
                    null, actor.username(), at);
            audit.record(actor, at, trade.getId(), "CONFIRMATION", conf.getId(), "ESCALATED", null, null,
                    "Escalated to supervisor after " + n + " chasers", null);
        }
    }

    private void send(Confirmation conf, CurrentUser actor, Instant at, String comment) {
        Trade trade = conf.getTrade();
        Counterparty c = trade.getCounterparty();
        Document finalDoc = latestOutbound(trade, Stage.FINAL);
        if (finalDoc == null || !terms.termsHash(trade).equals(finalDoc.getTermsHash())) {
            throw ApiException.conflict("No approved final document for the current economics");
        }
        boolean amended = finalDoc.getDocType() == DocType.AMENDED_CONFIRMATION;
        String subject = "%s%s Confirmation – %s – %s".formatted(amended ? "AMENDED: " : "",
                trade.getProductType().getDisplayName(), trade.getTradeRef(), trade.currencyPair());
        String body = """
                Dear %s team,

                Please find attached our %sconfirmation of the %s transaction traded on %s (our reference %s).

                Kindly check the economic terms and settlement instructions and return the confirmation signed \
                within %d hours, or affirm it in the Meridian Client Portal. Please notify us immediately of any \
                discrepancy.

                Kind regards,
                FX Confirmations · %s
                %s
                """.formatted(c.getLegalName(), amended ? "amended " : "", trade.getProductType().getDisplayName(),
                trade.getTradeDate(), trade.getTradeRef(), c.getConfirmationSlaHours(), props.bank().name(),
                props.bank().operationsEmail());
        MessageType type = amended ? MessageType.AMENDED_CONFIRMATION : MessageType.CONFIRMATION;
        if (c.getDeliveryChannel() == DeliveryChannel.PORTAL) {
            type = MessageType.PORTAL_NOTIFICATION;
            subject = "[Client Portal] New confirmation available – " + trade.getTradeRef();
        }
        outbox.queue(trade.getId(), type, recipientsOf(c), props.bank().operationsEmail(), subject, body,
                finalDoc.getId(), actor.username(), at);
        conf.setSentAt(at);
        conf.setSlaDueAt(at.plus(Duration.ofHours(c.getConfirmationSlaHours())));
        conf.setChaserCount(0);
        conf.setLastChasedAt(null);
        conf.setEscalated(false);
        transition(conf, AWAITING_RESPONSE, ConfirmationAction.SEND, actor, at, comment,
                "Delivered via " + c.getDeliveryChannel() + " to " + recipientsOf(c));
    }

    private void amend(Confirmation conf, AmendRequest req, CurrentUser actor, Instant at, String comment) {
        if (req == null) {
            throw ApiException.badRequest("Amendment details are required");
        }
        Trade t = conf.getTrade();
        List<String> changes = new ArrayList<>();
        t.setValueDate(change("Value date", t.getValueDate(), req.valueDate(), changes));
        t.setBuyAmount(change("Buy amount", t.getBuyAmount(), req.buyAmount(), changes));
        t.setSellAmount(change("Sell amount", t.getSellAmount(), req.sellAmount(), changes));
        t.setRate(change("Rate", t.getRate(), req.rate(), changes));
        t.setFarValueDate(change("Far value date", t.getFarValueDate(), req.farValueDate(), changes));
        t.setFarBuyAmount(change("Far buy amount", t.getFarBuyAmount(), req.farBuyAmount(), changes));
        t.setFarSellAmount(change("Far sell amount", t.getFarSellAmount(), req.farSellAmount(), changes));
        t.setFarRate(change("Far rate", t.getFarRate(), req.farRate(), changes));
        t.setFixingDate(change("Fixing date", t.getFixingDate(), req.fixingDate(), changes));
        t.setFixingSource(change("Fixing source", t.getFixingSource(),
                req.fixingSource() == null || req.fixingSource().isBlank() ? null : req.fixingSource(), changes));
        if (changes.isEmpty()) {
            throw ApiException.badRequest("The amendment does not change any economic term");
        }
        List<String> errors = TradeValidator.validate(EconomicTerms.of(t), t.getProductType());
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("Amendment rejected: " + String.join("; ", errors));
        }
        String from = conf.getStatus().name();
        t.setEconomicVersion(t.getEconomicVersion() + 1);
        t.setUpdatedAt(at);
        audit.record(actor, at, t.getId(), "TRADE", t.getId(), "TRADE_AMENDED", null, null,
                "Economic version " + t.getEconomicVersion() + ": " + String.join("; ", changes), comment);

        conf.setPreparedBy(actor.username());
        conf.setApprovedBy(null);
        conf.setDisputeReason(null);
        conf.setMatchedAt(null);
        boolean wasSent = conf.getSentAt() != null;
        Document d = documentService.generateOutbound(conf, wasSent ? DocType.AMENDED_CONFIRMATION : DocType.CONFIRMATION,
                Stage.DRAFT, at, actor);
        transition(conf, DRAFTED, ConfirmationAction.AMEND, actor, at, comment,
                "Amended from " + from + "; redrafted " + d.getFileName());
    }

    private void cancel(Confirmation conf, CurrentUser actor, Instant at, String comment) {
        Trade trade = conf.getTrade();
        if (conf.getSentAt() != null) {
            Document d = documentService.generateOutbound(conf, DocType.CANCELLATION, Stage.FINAL, at, actor);
            outbox.queue(trade.getId(), MessageType.CANCELLATION, recipientsOf(trade.getCounterparty()),
                    props.bank().operationsEmail(), "Cancellation – " + trade.getTradeRef(),
                    "Please find attached our notice of cancellation for transaction " + trade.getTradeRef()
                            + ". Reason: " + comment, d.getId(), actor.username(), at);
        }
        transition(conf, CANCELLED, ConfirmationAction.CANCEL, actor, at, comment,
                conf.getSentAt() != null ? "Cancellation notice issued to client" : "Cancelled before release");
    }

    private void guard(Confirmation conf, ConfirmationAction action, CurrentUser actor, String comment) {
        if (actor.role() == null) {
            return; // system
        }
        if (!action.getRoles().contains(actor.role())) {
            throw ApiException.forbidden("Role " + actor.role() + " cannot perform " + action.getLabel());
        }
        if (!action.getFrom().contains(conf.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot %s a confirmation in status %s".formatted(
                    action.getLabel().toLowerCase(), conf.getStatus().getDescription()));
        }
        if (action.isCommentRequired() && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest(action.getLabel() + " requires a comment");
        }
        if (action == ConfirmationAction.APPROVE && actor.username().equals(conf.getPreparedBy())) {
            throw ApiException.forbidden("Four-eyes principle: the preparer cannot approve the same confirmation");
        }
        if (actor.role() == Role.CLIENT && !Objects.equals(actor.counterpartyId(), conf.getTrade().getCounterparty().getId())) {
            throw ApiException.notFound("Trade", conf.getTrade().getId());
        }
    }

    private void transition(Confirmation conf, ConfirmationStatus to, ConfirmationAction action, CurrentUser actor,
                            Instant at, String comment, String details) {
        ConfirmationStatus from = conf.getStatus();
        conf.setStatus(to);
        conf.setStatusChangedAt(at);
        audit.record(actor, at, conf.getTrade().getId(), "CONFIRMATION", conf.getId(),
                action == null ? "INBOUND_" + to.name() : action.name(), from.name(), to.name(), details, comment);
    }

    private Document latestOutbound(Trade trade, Stage stage) {
        return documents.findFirstByTradeIdAndDirectionAndStageOrderByIdDesc(trade.getId(), Direction.OUTBOUND, stage)
                .orElse(null);
    }

    private static DocType outboundType(Trade trade) {
        return trade.getEconomicVersion() > 1 ? DocType.AMENDED_CONFIRMATION : DocType.CONFIRMATION;
    }

    private static List<CounterpartyContact> confirmationRecipients(Counterparty c) {
        return c.getContacts().stream()
                .filter(ct -> "CONFIRMATIONS".equals(ct.getRole()) || ct.isPrimary())
                .toList();
    }

    private static String recipientsOf(Counterparty c) {
        String list = confirmationRecipients(c).stream().map(CounterpartyContact::getEmail).distinct()
                .collect(Collectors.joining(", "));
        return list.isEmpty() ? "portal:" + c.getShortCode() : list;
    }

    private static <T> T change(String name, T current, T proposed, List<String> changes) {
        if (proposed == null || Objects.equals(normalize(current), normalize(proposed))) {
            return current;
        }
        changes.add("%s %s → %s".formatted(name, display(current), display(proposed)));
        return proposed;
    }

    private static Object normalize(Object o) {
        return o instanceof java.math.BigDecimal b ? b.stripTrailingZeros() : o;
    }

    private static String display(Object o) {
        return o instanceof java.math.BigDecimal b ? b.toPlainString() : String.valueOf(o);
    }

    public record AllowedAction(ConfirmationAction action, String label, boolean commentRequired,
                                boolean enabled, String disabledReason) {}
}
