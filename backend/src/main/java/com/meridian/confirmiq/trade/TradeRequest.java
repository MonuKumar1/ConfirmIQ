package com.meridian.confirmiq.trade;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Trade booking message from an upstream front-office system. Amounts are from the bank's perspective.")
public record TradeRequest(
        @NotBlank @Size(max = 60) String externalRef,
        @NotBlank @Size(max = 40) String sourceSystem,
        @NotNull ProductType productType,
        @NotBlank @Schema(description = "Counterparty short code or LEI") String counterparty,
        @NotNull LocalDate tradeDate,
        @NotNull Instant executionTime,
        @NotNull LocalDate valueDate,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String buyCurrency,
        @NotNull @Positive BigDecimal buyAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String sellCurrency,
        @NotNull @Positive BigDecimal sellAmount,
        @NotNull @Positive BigDecimal rate,
        LocalDate farValueDate,
        @Positive BigDecimal farBuyAmount,
        @Positive BigDecimal farSellAmount,
        @Positive BigDecimal farRate,
        @Pattern(regexp = "^$|[A-Z]{3}") String settlementCurrency,
        LocalDate fixingDate,
        @Size(max = 60) String fixingSource,
        @NotBlank @Size(max = 40) String book,
        @NotBlank @Size(max = 60) String trader) {}
