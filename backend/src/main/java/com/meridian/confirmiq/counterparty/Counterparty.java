package com.meridian.confirmiq.counterparty;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "counterparty")
@Getter
@Setter
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shortCode;
    private String legalName;
    private String lei;
    private String bic;
    private String country;

    @Enumerated(EnumType.STRING)
    private ClientType clientType;

    @Enumerated(EnumType.STRING)
    private DeliveryChannel deliveryChannel;

    private int confirmationSlaHours = 24;
    private String masterAgreement;
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "counterparty", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("isPrimary DESC, name ASC")
    private List<CounterpartyContact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "counterparty", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("currency ASC")
    private List<SettlementInstruction> settlementInstructions = new ArrayList<>();

    public void addContact(CounterpartyContact contact) {
        contact.setCounterparty(this);
        contacts.add(contact);
    }

    public void addSettlementInstruction(SettlementInstruction ssi) {
        ssi.setCounterparty(this);
        settlementInstructions.add(ssi);
    }

    public enum ClientType { BANK, ASSET_MANAGER, HEDGE_FUND, PENSION_FUND, INSURER, CORPORATE, SOVEREIGN }

    public enum DeliveryChannel { EMAIL, PORTAL }
}
