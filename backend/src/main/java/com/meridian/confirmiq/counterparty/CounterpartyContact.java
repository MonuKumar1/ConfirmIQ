package com.meridian.confirmiq.counterparty;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "counterparty_contact")
@Getter
@Setter
public class CounterpartyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    private String name;
    private String email;
    private String phone;

    /** e.g. CONFIRMATIONS, OPERATIONS, RELATIONSHIP_MANAGER, AUTHORISED_SIGNATORY */
    private String role;

    private boolean isPrimary;
}
