package com.meridian.confirmiq.template;

import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.counterparty.Counterparty;
import com.meridian.confirmiq.product.TermFormat;
import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.trade.Trade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templates;
    private final AuditService audit;
    private final AppProperties props;

    /** Counterparty-specific template wins over the product default. */
    @Transactional(readOnly = true)
    public TemplateVersion resolveActive(Trade trade) {
        ConfirmationTemplate tpl = templates
                .findFirstByProductTypeAndCounterpartyId(trade.getProductType(), trade.getCounterparty().getId())
                .or(() -> templates.findFirstByProductTypeAndCounterpartyIsNull(trade.getProductType()))
                .orElseThrow(() -> ApiException.conflict("No confirmation template configured for "
                        + trade.getProductType().getDisplayName()));
        return tpl.getVersions().stream().filter(TemplateVersion::isActive).findFirst()
                .orElseThrow(() -> ApiException.conflict("Template " + tpl.getCode() + " has no active version"));
    }

    public RenderedText render(TemplateVersion v, Trade trade) {
        Counterparty c = trade.getCounterparty();
        Map<String, String> vars = Map.of(
                "counterpartyName", c.getLegalName(),
                "bankName", props.bank().name(),
                "tradeRef", trade.getTradeRef(),
                "productName", trade.getProductType().getDisplayName(),
                "tradeDate", TermFormat.date(trade.getTradeDate()),
                "masterAgreement", c.getMasterAgreement() == null ? "the applicable master agreement" : c.getMasterAgreement(),
                "operationsEmail", props.bank().operationsEmail());
        List<String> clauses = Arrays.stream(substitute(v.getClauses(), vars).split("\\n\\s*\\n"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new RenderedText(substitute(v.getTitle(), vars), substitute(v.getPreamble(), vars), clauses,
                substitute(v.getClosing(), vars), v.getId());
    }

    @Transactional(readOnly = true)
    public List<TemplateSummary> list() {
        return templates.findAll(Sort.by("productType", "code")).stream().map(TemplateSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public TemplateDetail get(Long id) {
        return TemplateDetail.of(find(id));
    }

    @Transactional
    public TemplateDetail createVersion(Long id, VersionRequest req) {
        ConfirmationTemplate tpl = find(id);
        int next = tpl.getVersions().stream().mapToInt(TemplateVersion::getVersion).max().orElse(0) + 1;
        TemplateVersion v = new TemplateVersion();
        v.setVersion(next);
        v.setTitle(req.title());
        v.setPreamble(req.preamble());
        v.setClauses(req.clauses());
        v.setClosing(req.closing());
        v.setChangeNote(req.changeNote());
        v.setCreatedBy(CurrentUser.get().username());
        tpl.addVersion(v);
        audit.staticData("TEMPLATE", id, "TEMPLATE_VERSION_CREATED", tpl.getCode() + " v" + next + ": " + req.changeNote());
        return TemplateDetail.of(tpl);
    }

    @Transactional
    public TemplateDetail activate(Long id, int version) {
        ConfirmationTemplate tpl = find(id);
        TemplateVersion target = tpl.getVersions().stream().filter(v -> v.getVersion() == version).findFirst()
                .orElseThrow(() -> ApiException.notFound("Template version", version));
        tpl.getVersions().forEach(v -> v.setActive(v == target));
        audit.staticData("TEMPLATE", id, "TEMPLATE_VERSION_ACTIVATED", tpl.getCode() + " v" + version);
        return TemplateDetail.of(tpl);
    }

    public ConfirmationTemplate find(Long id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("Template", id));
    }

    private static String substitute(String text, Map<String, String> vars) {
        String out = text;
        for (var e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return out;
    }

    public record RenderedText(String title, String preamble, List<String> clauses, String closing, Long templateVersionId) {}

    public record TemplateSummary(Long id, String code, String name, String productType, String counterparty,
                                  Integer activeVersion, int versionCount, Instant lastChanged) {
        static TemplateSummary of(ConfirmationTemplate t) {
            TemplateVersion active = t.getVersions().stream().filter(TemplateVersion::isActive).findFirst().orElse(null);
            return new TemplateSummary(t.getId(), t.getCode(), t.getName(), t.getProductType().name(),
                    t.getCounterparty() == null ? null : t.getCounterparty().getLegalName(),
                    active == null ? null : active.getVersion(), t.getVersions().size(),
                    t.getVersions().isEmpty() ? t.getCreatedAt() : t.getVersions().getFirst().getCreatedAt());
        }
    }

    public record VersionView(Long id, int version, String title, String preamble, String clauses, String closing,
                              String changeNote, boolean active, String createdBy, Instant createdAt) {
        static VersionView of(TemplateVersion v) {
            return new VersionView(v.getId(), v.getVersion(), v.getTitle(), v.getPreamble(), v.getClauses(),
                    v.getClosing(), v.getChangeNote(), v.isActive(), v.getCreatedBy(), v.getCreatedAt());
        }
    }

    public record TemplateDetail(Long id, String code, String name, String productType, String counterparty,
                                 List<VersionView> versions) {
        static TemplateDetail of(ConfirmationTemplate t) {
            return new TemplateDetail(t.getId(), t.getCode(), t.getName(), t.getProductType().name(),
                    t.getCounterparty() == null ? null : t.getCounterparty().getLegalName(),
                    t.getVersions().stream().map(VersionView::of).toList());
        }
    }

    public record VersionRequest(@NotBlank @Size(max = 200) String title, @NotBlank String preamble,
                                 @NotBlank String clauses, @NotBlank String closing,
                                 @NotBlank @Size(max = 300) String changeNote) {}
}
