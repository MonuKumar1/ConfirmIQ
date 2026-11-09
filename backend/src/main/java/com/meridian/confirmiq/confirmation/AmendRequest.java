package com.meridian.confirmiq.confirmation;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Economic fields that may be corrected by an amendment; null means unchanged. */
public record AmendRequest(
        LocalDate valueDate,
        @Positive BigDecimal buyAmount,
        @Positive BigDecimal sellAmount,
        @Positive BigDecimal rate,
        LocalDate farValueDate,
        @Positive BigDecimal farBuyAmount,
        @Positive BigDecimal farSellAmount,
        @Positive BigDecimal farRate,
        LocalDate fixingDate,
        String fixingSource) {}
