package com.meridian.confirmiq.trade;

import com.meridian.confirmiq.trade.TradeValidator.EconomicTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TradeValidatorTest {

    private static final LocalDate TUE = LocalDate.of(2026, 10, 27);

    private static EconomicTerms spot(LocalDate valueDate, String buyAmt, String sellAmt, String rate) {
        return new EconomicTerms(TUE, valueDate, "EUR", new BigDecimal(buyAmt), "USD", new BigDecimal(sellAmt),
                new BigDecimal(rate), null, null, null, null, null, null, null);
    }

    @Test
    void acceptsConsistentSpot() {
        assertThat(TradeValidator.validate(spot(TUE.plusDays(2), "1000000", "1085300", "1.0853"), ProductType.FX_SPOT))
                .isEmpty();
    }

    @Test
    void detectsRateAmountInconsistencyUsingMarketConvention() {
        // Bank buys USD / sells EUR – EUR is still the base currency of the pair.
        var terms = new EconomicTerms(TUE, TUE.plusDays(2), "USD", new BigDecimal("1150000"), "EUR",
                new BigDecimal("1000000"), new BigDecimal("1.0853"), null, null, null, null, null, null, null);
        assertThat(TradeValidator.validate(terms, ProductType.FX_SPOT)).singleElement().asString()
                .contains("EUR/USD");
    }

    @Test
    void enforcesSpotAndForwardValueDateConventions() {
        assertThat(TradeValidator.validate(spot(TUE.plusDays(7), "1000000", "1085300", "1.0853"), ProductType.FX_SPOT))
                .anyMatch(e -> e.contains("T+2"));
        assertThat(TradeValidator.validate(spot(TUE.plusDays(2), "1000000", "1085300", "1.0853"), ProductType.FX_FORWARD))
                .anyMatch(e -> e.contains("after the spot date"));
        assertThat(TradeValidator.validate(spot(LocalDate.of(2026, 10, 31), "1000000", "1085300", "1.0853"), ProductType.FX_FORWARD))
                .anyMatch(e -> e.contains("weekend"));
    }

    @Test
    void currencyPairFollowsQuotationHierarchy() {
        assertThat(CurrencyPair.of("JPY", "USD").code()).isEqualTo("USD/JPY");
        assertThat(CurrencyPair.of("USD", "GBP").code()).isEqualTo("GBP/USD");
        assertThat(CurrencyPair.of("GBP", "EUR").code()).isEqualTo("EUR/GBP");
    }
}
