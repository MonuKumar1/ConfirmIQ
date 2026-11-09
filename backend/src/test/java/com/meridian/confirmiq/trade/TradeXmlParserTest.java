package com.meridian.confirmiq.trade;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeXmlParserTest {

    private final TradeXmlParser parser;

    TradeXmlParserTest() throws Exception {
        parser = new TradeXmlParser();
    }

    @Test
    void parsesSampleBatchWithAllProducts() throws Exception {
        List<TradeRequest> trades = parser.parse(Files.readAllBytes(Path.of("../samples/trades/booking-batch-mixed.xml")));
        assertThat(trades).extracting(TradeRequest::productType)
                .containsExactly(ProductType.FX_SPOT, ProductType.FX_FORWARD, ProductType.FX_SWAP, ProductType.FX_NDF);
        TradeRequest swap = trades.get(2);
        assertThat(swap.farRate()).isEqualByComparingTo("150.01");
        assertThat(swap.sourceSystem()).isEqualTo("MUREX-FX");
        assertThat(trades.get(3).fixingSource()).isEqualTo("INR RBIB (INR01)");
    }

    @Test
    void rejectsSchemaViolations() {
        String xml = """
                <tradeBooking xmlns="urn:meridian:confirmiq:booking:1.0">
                  <header><messageId>x</messageId><sourceSystem>T</sourceSystem><createdAt>2026-01-01T00:00:00Z</createdAt></header>
                  <trade><externalRef>A</externalRef><productType>FX_OPTION</productType></trade>
                </tradeBooking>""";
        assertThatThrownBy(() -> parser.parse(xml.getBytes()))
                .isInstanceOf(TradeXmlParser.XmlRejectedException.class)
                .hasMessageContaining("Schema violation");
    }

    @Test
    void rejectsDoctypeToPreventXxe() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        assertThatThrownBy(() -> parser.parse(xml.getBytes()))
                .isInstanceOf(TradeXmlParser.XmlRejectedException.class);
    }
}
