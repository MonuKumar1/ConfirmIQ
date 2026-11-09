package com.meridian.confirmiq.document;

import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.common.Hashing;
import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.confirmation.Confirmation;
import com.meridian.confirmiq.counterparty.Counterparty;
import com.meridian.confirmiq.counterparty.CounterpartyContact;
import com.meridian.confirmiq.counterparty.SettlementInstruction;
import com.meridian.confirmiq.counterparty.SettlementInstructionRepository;
import com.meridian.confirmiq.document.ConfirmationPdfModel.SsiLine;
import com.meridian.confirmiq.document.Document.*;
import com.meridian.confirmiq.product.ProductTermsService;
import com.meridian.confirmiq.product.TermField;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.template.ConfirmationTemplate;
import com.meridian.confirmiq.template.TemplateService;
import com.meridian.confirmiq.template.TemplateService.RenderedText;
import com.meridian.confirmiq.template.TemplateVersion;
import com.meridian.confirmiq.trade.ProductType;
import com.meridian.confirmiq.trade.Trade;
import com.meridian.confirmiq.trade.TradeRepository;
import com.meridian.confirmiq.user.AppUser;
import com.meridian.confirmiq.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final ConfirmationPdfRenderer renderer;
    private final ProductTermsService terms;
    private final TemplateService templates;
    private final SettlementInstructionRepository ssis;
    private final TradeRepository trades;
    private final AuditService audit;
    private final AppProperties props;
    private final AppUserRepository users;

    /** Generates house paper for the trade's current economics and records it in the repository. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Document generateOutbound(Confirmation conf, DocType type, Stage stage, Instant at, CurrentUser actor) {
        Trade trade = conf.getTrade();
        int version = documents.maxVersion(trade.getId(), Direction.OUTBOUND) + 1;
        String termsHash = terms.termsHash(trade);
        RenderedText text = type == DocType.CANCELLATION ? cancellationText(trade) : templates.render(templates.resolveActive(trade), trade);
        String title = type == DocType.AMENDED_CONFIRMATION ? "Amended " + text.title() : text.title();

        ConfirmationPdfModel model = buildModel(trade, type, stage, version, title, text, termsHash,
                conf.getPreparedBy() == null ? actor.fullName() : displayName(conf.getPreparedBy()),
                displayName(conf.getApprovedBy()), at);
        byte[] pdf = renderer.render(model);

        String fileName = "%s_%s_v%d_%s.pdf".formatted(trade.getTradeRef(), type.name().toLowerCase(), version,
                stage.name().toLowerCase());
        Document d = new Document();
        d.setTrade(trade);
        d.setDocType(type);
        d.setDirection(Direction.OUTBOUND);
        d.setStage(stage);
        d.setVersion(version);
        d.setFileName(fileName);
        d.setStorageKey(storage.store(pdf, fileName));
        d.setSha256(Hashing.sha256(pdf));
        d.setSizeBytes(pdf.length);
        d.setTermsHash(termsHash);
        d.setTemplateVersionId(text.templateVersionId());
        d.setSource(Source.GENERATED);
        d.setCreatedBy(actor.username());
        d.setCreatedAt(at);
        documents.save(d);
        audit.record(actor, at, trade.getId(), "DOCUMENT", d.getId(), "DOCUMENT_GENERATED", null, null,
                "%s v%d (%s) · sha256 %s".formatted(type, version, stage, d.getSha256().substring(0, 12)), null);
        return d;
    }

    /** Stores a received document (not yet linked to a trade). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Document storeInbound(byte[] content, String fileName, Source source, String actor, Instant at) {
        Document d = new Document();
        d.setDocType(DocType.CLIENT_RESPONSE);
        d.setDirection(Direction.INBOUND);
        d.setStage(Stage.RECEIVED);
        d.setVersion(1);
        d.setFileName(fileName);
        d.setStorageKey(storage.store(content, fileName));
        d.setSha256(Hashing.sha256(content));
        d.setSizeBytes(content.length);
        d.setSource(source);
        d.setCreatedBy(actor);
        d.setCreatedAt(at);
        return documents.save(d);
    }

    @Transactional(readOnly = true)
    public byte[] previewTemplate(Long templateId, int version) {
        ConfirmationTemplate tpl = templates.find(templateId);
        TemplateVersion v = tpl.getVersions().stream().filter(x -> x.getVersion() == version).findFirst()
                .orElseThrow(() -> ApiException.notFound("Template version", version));
        Trade sample = trades.findAll(PageRequest.of(0, 200)).stream()
                .filter(t -> t.getProductType() == tpl.getProductType())
                .filter(t -> tpl.getCounterparty() == null || t.getCounterparty().getId().equals(tpl.getCounterparty().getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("No " + tpl.getProductType().getDisplayName()
                        + " trade available to render a specimen"));
        RenderedText text = templates.render(v, sample);
        ConfirmationPdfModel model = buildModel(sample, DocType.CONFIRMATION, Stage.DRAFT, 0,
                text.title() + " (Specimen)", text, terms.termsHash(sample), CurrentUser.get().fullName(), null,
                Instant.now());
        return renderer.render(model);
    }

    public byte[] content(Document d) {
        return storage.read(d.getStorageKey());
    }

    public Document find(Long id) {
        return documents.findById(id).orElseThrow(() -> ApiException.notFound("Document", id));
    }

    private ConfirmationPdfModel buildModel(Trade trade, DocType type, Stage stage, int version, String title,
                                            RenderedText text, String termsHash, String preparedBy,
                                            String approvedBy, Instant at) {
        Counterparty cpty = trade.getCounterparty();
        CounterpartyContact attention = cpty.getContacts().stream()
                .filter(c -> "CONFIRMATIONS".equals(c.getRole()) || c.isPrimary())
                .findFirst().orElse(null);
        List<TermField> fields = terms.termsOf(trade);
        String fingerprint = String.join("-", termsHash.substring(0, 4), termsHash.substring(4, 8),
                termsHash.substring(8, 12), termsHash.substring(12, 16)).toUpperCase();
        return new ConfirmationPdfModel(type, stage, version, trade.getEconomicVersion(), trade.getTradeRef(),
                title, text.preamble(), text.clauses(), text.closing(), props.bank(),
                cpty.getLegalName(), cpty.getLei(),
                attention == null ? null : attention.getName(), attention == null ? null : attention.getEmail(),
                fields, type == DocType.CANCELLATION ? List.of() : settlementLines(trade),
                QrCodes.payload(trade.getTradeRef(), trade.getEconomicVersion(), termsHash), fingerprint,
                preparedBy, approvedBy, at);
    }

    /** Payment flows: we receive what we buy and pay what we sell (reversed on a swap far leg). */
    private List<SsiLine> settlementLines(Trade t) {
        String house = props.bank().shortName();
        String cpty = t.getCounterparty().getShortCode();
        List<SsiLine> lines = new ArrayList<>();
        boolean ndf = t.getProductType() == ProductType.FX_NDF;
        List<String> currencies = ndf ? List.of(t.getSettlementCurrency()) : List.of(t.getBuyCurrency(), t.getSellCurrency());
        for (String ccy : currencies) {
            ssis.findHouseInstruction(ccy).ifPresent(s ->
                    lines.add(line(house + " receives " + ccy + (ndf ? " (net)" : ""), s)));
            ssis.findFirstByCounterpartyIdAndCurrencyAndActiveTrueOrderByEffectiveFromDesc(t.getCounterparty().getId(), ccy)
                    .ifPresent(s -> lines.add(line(cpty + " receives " + ccy + (ndf ? " (net)" : ""), s)));
        }
        return lines;
    }

    private static SsiLine line(String flow, SettlementInstruction s) {
        return new SsiLine(flow, s.getBeneficiaryName(), s.getBankName(), s.getBankBic(), s.getAccountNumber(),
                s.getIntermediaryBic());
    }

    private RenderedText cancellationText(Trade trade) {
        return new RenderedText(
                "Cancellation of " + trade.getProductType().getDisplayName() + " Transaction",
                "We refer to the transaction identified below, previously confirmed between %s (\"Party A\") and %s (\"Party B\"). "
                        .formatted(props.bank().name(), trade.getCounterparty().getLegalName())
                        + "This notice confirms that the parties have agreed to cancel the transaction in full with "
                        + "immediate effect. No settlement obligations remain outstanding in respect of it.",
                List.of("Any confirmation previously issued for this transaction is hereby superseded and of no further effect.",
                        "Please contact " + props.bank().operationsEmail() + " immediately if you do not agree with this cancellation."),
                "Cancelled terms are reproduced below for reference only.",
                null);
    }

    private String displayName(String username) {
        if (username == null) {
            return null;
        }
        return users.findByUsernameIgnoreCase(username).map(AppUser::getFullName).orElse(username);
    }
}
