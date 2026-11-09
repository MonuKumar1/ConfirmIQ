package com.meridian.confirmiq.counterparty;

import com.meridian.confirmiq.counterparty.Counterparty.ClientType;
import com.meridian.confirmiq.counterparty.Counterparty.DeliveryChannel;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class CounterpartyDtos {

    private CounterpartyDtos() {}

    public record CounterpartySummary(Long id, String shortCode, String legalName, String lei, String country,
                                      ClientType clientType, DeliveryChannel deliveryChannel,
                                      int confirmationSlaHours, boolean active,
                                      long openConfirmations, long breaks) {}

    public record CounterpartyDetail(Long id, String shortCode, String legalName, String lei, String bic,
                                     String country, ClientType clientType, DeliveryChannel deliveryChannel,
                                     int confirmationSlaHours, String masterAgreement, boolean active,
                                     List<ContactView> contacts, List<SsiView> settlementInstructions) {

        static CounterpartyDetail of(Counterparty c) {
            return new CounterpartyDetail(c.getId(), c.getShortCode(), c.getLegalName(), c.getLei(), c.getBic(),
                    c.getCountry(), c.getClientType(), c.getDeliveryChannel(), c.getConfirmationSlaHours(),
                    c.getMasterAgreement(), c.isActive(),
                    c.getContacts().stream().map(ContactView::of).toList(),
                    c.getSettlementInstructions().stream().filter(SettlementInstruction::isActive)
                            .map(SsiView::of).toList());
        }
    }

    public record CounterpartyRequest(
            @NotBlank @Size(max = 20) String shortCode,
            @NotBlank @Size(max = 200) String legalName,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{20}", message = "LEI must be 20 upper-case alphanumeric characters") String lei,
            @Pattern(regexp = "^$|[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?", message = "Invalid BIC") String bic,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "ISO 3166 alpha-2 country code") String country,
            @NotNull ClientType clientType,
            @NotNull DeliveryChannel deliveryChannel,
            @Min(1) @Max(240) int confirmationSlaHours,
            @Size(max = 100) String masterAgreement,
            boolean active) {}

    public record ContactView(Long id, String name, String email, String phone, String role, boolean primary) {
        static ContactView of(CounterpartyContact c) {
            return new ContactView(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getRole(), c.isPrimary());
        }
    }

    public record ContactRequest(@NotBlank String name, @NotBlank @Email String email, String phone,
                                 @NotBlank String role, boolean primary) {}

    public record SsiView(Long id, String currency, String beneficiaryName, String bankName, String bankBic,
                          String accountNumber, String intermediaryBic, LocalDate effectiveFrom) {
        public static SsiView of(SettlementInstruction s) {
            return new SsiView(s.getId(), s.getCurrency(), s.getBeneficiaryName(), s.getBankName(), s.getBankBic(),
                    s.getAccountNumber(), s.getIntermediaryBic(), s.getEffectiveFrom());
        }
    }

    public record SsiRequest(
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "ISO 4217 currency code") String currency,
            @NotBlank String beneficiaryName,
            @NotBlank String bankName,
            @NotBlank @Pattern(regexp = "[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?", message = "Invalid BIC") String bankBic,
            @NotBlank String accountNumber,
            @Pattern(regexp = "^$|[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?", message = "Invalid BIC") String intermediaryBic,
            @NotNull LocalDate effectiveFrom) {}
}
