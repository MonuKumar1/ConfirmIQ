package com.meridian.confirmiq.counterparty;

import com.meridian.confirmiq.audit.AuditService;
import com.meridian.confirmiq.common.ApiException;
import com.meridian.confirmiq.confirmation.ConfirmationRepository;
import com.meridian.confirmiq.confirmation.ConfirmationStatus;
import com.meridian.confirmiq.counterparty.CounterpartyDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounterpartyService {

    private final CounterpartyRepository counterparties;
    private final SettlementInstructionRepository ssis;
    private final ConfirmationRepository confirmations;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<CounterpartySummary> list() {
        Map<Long, Long> open = toMap(confirmations.countByCounterpartyAndStatusNotIn(ConfirmationStatus.TERMINAL));
        Map<Long, Long> breaks = toMap(confirmations.countByCounterpartyAndStatusIn(ConfirmationStatus.BREAKS));
        return counterparties.findAll(Sort.by("legalName")).stream()
                .map(c -> new CounterpartySummary(c.getId(), c.getShortCode(), c.getLegalName(), c.getLei(),
                        c.getCountry(), c.getClientType(), c.getDeliveryChannel(), c.getConfirmationSlaHours(),
                        c.isActive(), open.getOrDefault(c.getId(), 0L), breaks.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CounterpartyDetail get(Long id) {
        return CounterpartyDetail.of(find(id));
    }

    @Transactional
    public CounterpartyDetail create(CounterpartyRequest req) {
        if (counterparties.existsByShortCodeIgnoreCase(req.shortCode())) {
            throw ApiException.conflict("Short code " + req.shortCode() + " is already in use");
        }
        if (counterparties.existsByLei(req.lei())) {
            throw ApiException.conflict("LEI " + req.lei() + " is already registered");
        }
        Counterparty c = new Counterparty();
        apply(c, req);
        counterparties.save(c);
        audit.staticData("COUNTERPARTY", c.getId(), "COUNTERPARTY_CREATED", c.getLegalName());
        return CounterpartyDetail.of(c);
    }

    @Transactional
    public CounterpartyDetail update(Long id, CounterpartyRequest req) {
        Counterparty c = find(id);
        counterparties.findByShortCodeIgnoreCase(req.shortCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw ApiException.conflict("Short code already in use"); });
        apply(c, req);
        audit.staticData("COUNTERPARTY", id, "COUNTERPARTY_UPDATED", c.getLegalName());
        return CounterpartyDetail.of(c);
    }

    @Transactional
    public CounterpartyDetail addContact(Long id, ContactRequest req) {
        Counterparty c = find(id);
        if (req.primary()) {
            c.getContacts().forEach(existing -> existing.setPrimary(false));
        }
        CounterpartyContact contact = new CounterpartyContact();
        contact.setName(req.name());
        contact.setEmail(req.email());
        contact.setPhone(req.phone());
        contact.setRole(req.role());
        contact.setPrimary(req.primary());
        c.addContact(contact);
        audit.staticData("COUNTERPARTY", id, "CONTACT_ADDED", req.name() + " <" + req.email() + ">");
        return CounterpartyDetail.of(c);
    }

    @Transactional
    public CounterpartyDetail removeContact(Long id, Long contactId) {
        Counterparty c = find(id);
        boolean removed = c.getContacts().removeIf(ct -> ct.getId().equals(contactId));
        if (!removed) {
            throw ApiException.notFound("Contact", contactId);
        }
        audit.staticData("COUNTERPARTY", id, "CONTACT_REMOVED", "contact #" + contactId);
        return CounterpartyDetail.of(c);
    }

    @Transactional
    public CounterpartyDetail addSsi(Long id, SsiRequest req) {
        Counterparty c = find(id);
        // A new SSI for a currency supersedes the previous one; history is retained inactive.
        c.getSettlementInstructions().stream()
                .filter(s -> s.isActive() && s.getCurrency().equals(req.currency()))
                .forEach(s -> s.setActive(false));
        c.addSettlementInstruction(toSsi(req));
        audit.staticData("COUNTERPARTY", id, "SSI_UPDATED",
                req.currency() + " → " + req.bankBic() + " / " + req.accountNumber());
        return CounterpartyDetail.of(c);
    }

    @Transactional
    public CounterpartyDetail deactivateSsi(Long id, Long ssiId) {
        Counterparty c = find(id);
        SettlementInstruction ssi = c.getSettlementInstructions().stream()
                .filter(s -> s.getId().equals(ssiId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("SSI", ssiId));
        ssi.setActive(false);
        audit.staticData("COUNTERPARTY", id, "SSI_DEACTIVATED", ssi.getCurrency() + " SSI #" + ssiId);
        return CounterpartyDetail.of(c);
    }

    @Transactional(readOnly = true)
    public List<SsiView> houseInstructions() {
        return ssis.findHouseInstructions().stream().map(SsiView::of).toList();
    }

    public Counterparty find(Long id) {
        return counterparties.findById(id).orElseThrow(() -> ApiException.notFound("Counterparty", id));
    }

    static SettlementInstruction toSsi(SsiRequest req) {
        SettlementInstruction s = new SettlementInstruction();
        s.setCurrency(req.currency());
        s.setBeneficiaryName(req.beneficiaryName());
        s.setBankName(req.bankName());
        s.setBankBic(req.bankBic());
        s.setAccountNumber(req.accountNumber());
        s.setIntermediaryBic(blankToNull(req.intermediaryBic()));
        s.setEffectiveFrom(req.effectiveFrom());
        return s;
    }

    private static void apply(Counterparty c, CounterpartyRequest req) {
        c.setShortCode(req.shortCode().toUpperCase());
        c.setLegalName(req.legalName());
        c.setLei(req.lei());
        c.setBic(blankToNull(req.bic()));
        c.setCountry(req.country());
        c.setClientType(req.clientType());
        c.setDeliveryChannel(req.deliveryChannel());
        c.setConfirmationSlaHours(req.confirmationSlaHours());
        c.setMasterAgreement(blankToNull(req.masterAgreement()));
        c.setActive(req.active());
        c.setUpdatedAt(Instant.now());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1], Long::sum));
    }

}
