package com.meridian.confirmiq.counterparty;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Standard Settlement Instruction. A null counterparty denotes the house (our own) instruction. */
@Entity
@Table(name = "settlement_instruction")
@Getter
@Setter
public class SettlementInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    private String currency;
    private String beneficiaryName;
    private String bankName;
    private String bankBic;
    private String accountNumber;
    private String intermediaryBic;
    private LocalDate effectiveFrom;
    private boolean active = true;
}
