package com.meridian.confirmiq.inbound;

import com.meridian.confirmiq.inbound.MatchingEngine.FieldResult;
import com.meridian.confirmiq.inbound.MatchingEngine.FieldStatus;
import com.meridian.confirmiq.product.TermField;
import com.meridian.confirmiq.product.TermField.MatchType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    private final List<TermField> expected = List.of(
            new TermField("tradeRef", "Our Reference", "Your Reference", "MC-FX-2026-000001", MatchType.TEXT),
            new TermField("valueDate", "Value Date", "Value Date", "29 Oct 2026", MatchType.DATE),
            new TermField("buyAmount", "Buys", "We Sell", "EUR 5,000,000.00", MatchType.AMOUNT),
            new TermField("rate", "Exchange Rate", "Exchange Rate", "1.0853", MatchType.RATE));

    @Test
    void matchesEquivalentValuesInDifferentFormats() {
        List<FieldResult> r = engine.compare(expected, Map.of(
                "tradeRef", "mc-fx-2026-000001",
                "valueDate", "2026-10-29",
                "buyAmount", "EUR 5000000.004",
                "rate", "1.085300"));
        assertThat(MatchingEngine.allMatch(r)).isTrue();
    }

    @Test
    void flagsBreaksAndMissingFieldsWithDifference() {
        List<FieldResult> r = engine.compare(expected, Map.of(
                "tradeRef", "MC-FX-2026-000001",
                "valueDate", "30 Oct 2026",
                "buyAmount", "EUR 5,002,500.00"));
        assertThat(MatchingEngine.allMatch(r)).isFalse();
        assertThat(r).extracting(FieldResult::status)
                .containsExactly(FieldStatus.MATCH, FieldStatus.MISMATCH, FieldStatus.MISMATCH, FieldStatus.MISSING);
        assertThat(r.get(1).difference()).isEqualTo("Δ 1 day(s)");
        assertThat(r.get(2).difference()).isEqualTo("Δ 2500.00 EUR");
    }

    @Test
    void rateOutsideToleranceIsABreak() {
        List<FieldResult> r = engine.compare(expected.subList(3, 4), Map.of("rate", "1.08531"));
        assertThat(r.getFirst().status()).isEqualTo(FieldStatus.MISMATCH);
    }

    @Test
    void currencyMismatchIsABreakEvenWhenAmountsAgree() {
        List<FieldResult> r = engine.compare(expected.subList(2, 3), Map.of("buyAmount", "USD 5,000,000.00"));
        assertThat(r.getFirst().status()).isEqualTo(FieldStatus.MISMATCH);
    }
}
