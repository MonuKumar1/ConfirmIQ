package com.meridian.confirmiq.inbound;

import com.meridian.confirmiq.product.TermField;
import com.meridian.confirmiq.product.TermField.MatchType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTermExtractorTest {

    private final PdfTermExtractor extractor = new PdfTermExtractor();

    private final List<TermField> fields = List.of(
            new TermField("tradeRef", "Our Reference", "Your Reference", "MC-FX-2026-000042", MatchType.TEXT),
            new TermField("valueDate", "Value Date", "Value Date", "29 Oct 2026", MatchType.DATE),
            new TermField("farValueDate", "Far Value Date", "Far Value Date", "29 Jan 2027", MatchType.DATE),
            new TermField("buyAmount", "Meridian Capital Buys", "We Sell", "EUR 1,000,000.00", MatchType.AMOUNT));

    @Test
    void readsCounterpartyPaperUsingTheirLabels() {
        String text = """
                HELIX GLOBAL MACRO FUND LTD.
                Our Reference TRS-700042
                Your Reference MC-FX-2026-000042
                Value Date 29 Oct 2026
                Far Value Date 29 Jan 2027
                We Sell EUR 1,000,000.00
                """;
        var result = extractor.extract(text, fields);
        assertThat(result.counterpartyPaper()).isTrue();
        assertThat(result.values()).containsEntry("tradeRef", "MC-FX-2026-000042")
                .containsEntry("valueDate", "29 Oct 2026")
                .containsEntry("farValueDate", "29 Jan 2027")
                .containsEntry("buyAmount", "EUR 1,000,000.00");
        assertThat(extractor.findTradeRef(text)).contains("MC-FX-2026-000042");
    }

    @Test
    void readsHousePaperAndDoesNotConfuseNearAndFarDates() {
        String text = """
                Our Reference MC-FX-2026-000042
                Far Value Date 29 Jan 2027
                Value Date 29 Oct 2026
                Meridian Capital Buys EUR 1,000,000.00
                """;
        var result = extractor.extract(text, fields);
        assertThat(result.counterpartyPaper()).isFalse();
        assertThat(result.values().get("valueDate")).isEqualTo("29 Oct 2026");
        assertThat(result.values().get("farValueDate")).isEqualTo("29 Jan 2027");
    }
}
