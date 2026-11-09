package com.meridian.confirmiq.trade;

import com.meridian.confirmiq.counterparty.Counterparty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An FX trade as booked by the front office. Economics are expressed from the house
 * (Meridian Capital) perspective: we buy {@code buyAmount} of {@code buyCurrency} and
 * sell {@code sellAmount} of {@code sellCurrency}. For swaps the far leg reverses the flows.
 */
@Entity
@Table(name = "trade")
@Getter
@Setter
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tradeRef;
    private String externalRef;
    private String sourceSystem;

    @Enumerated(EnumType.STRING)
    private IngestionChannel ingestionChannel;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    private LocalDate tradeDate;
    private Instant executionTime;
    private LocalDate valueDate;

    private String buyCurrency;
    private BigDecimal buyAmount;
    private String sellCurrency;
    private BigDecimal sellAmount;
    private BigDecimal rate;

    private LocalDate farValueDate;
    private BigDecimal farBuyAmount;
    private BigDecimal farSellAmount;
    private BigDecimal farRate;

    private String settlementCurrency;
    private LocalDate fixingDate;
    private String fixingSource;

    private String book;
    private String trader;

    /** Incremented on every economic amendment; drives document versioning. */
    private int economicVersion = 1;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Version
    private long lockVersion;

    public String currencyPair() {
        return CurrencyPair.of(buyCurrency, sellCurrency).code();
    }

    public enum IngestionChannel { XML_UPLOAD, API, SEED }
}
