package com.meridian.confirmiq.trade;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Parses {@code tradeBooking} XML messages after validating them against the published XSD. */
@Component
public class TradeXmlParser {

    public static final String NS = "urn:meridian:confirmiq:booking:1.0";
    private final Schema schema;

    public TradeXmlParser() throws IOException, SAXException {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var in = new ClassPathResource("xsd/trade-booking-v1.xsd").getInputStream()) {
            this.schema = sf.newSchema(new javax.xml.transform.stream.StreamSource(in));
        }
    }

    public List<TradeRequest> parse(byte[] xml) {
        org.w3c.dom.Document doc = parseSecure(xml);
        try {
            schema.newValidator().validate(new DOMSource(doc));
        } catch (SAXParseException e) {
            throw new XmlRejectedException("Schema violation at line %d: %s".formatted(e.getLineNumber(), e.getMessage()));
        } catch (SAXException | IOException e) {
            throw new XmlRejectedException("Schema validation failed: " + e.getMessage());
        }
        Element root = doc.getDocumentElement();
        Element header = child(root, "header");
        String sourceSystem = text(header, "sourceSystem");
        List<TradeRequest> trades = new ArrayList<>();
        NodeList nodes = root.getElementsByTagNameNS(NS, "trade");
        for (int i = 0; i < nodes.getLength(); i++) {
            trades.add(toRequest((Element) nodes.item(i), sourceSystem));
        }
        return trades;
    }

    private static TradeRequest toRequest(Element t, String sourceSystem) {
        Element near = child(t, "nearLeg");
        Element far = child(t, "farLeg");
        Element ndf = child(t, "ndf");
        return new TradeRequest(
                text(t, "externalRef"), sourceSystem, ProductType.valueOf(text(t, "productType")),
                text(t, "counterparty"), LocalDate.parse(text(t, "tradeDate")),
                OffsetDateTime.parse(text(t, "executionTime")).toInstant(),
                LocalDate.parse(text(near, "valueDate")),
                text(near, "buyCurrency"), new BigDecimal(text(near, "buyAmount")),
                text(near, "sellCurrency"), new BigDecimal(text(near, "sellAmount")),
                new BigDecimal(text(near, "rate")),
                far == null ? null : LocalDate.parse(text(far, "valueDate")),
                far == null ? null : new BigDecimal(text(far, "buyAmount")),
                far == null ? null : new BigDecimal(text(far, "sellAmount")),
                far == null ? null : new BigDecimal(text(far, "rate")),
                ndf == null ? null : text(ndf, "settlementCurrency"),
                ndf == null ? null : LocalDate.parse(text(ndf, "fixingDate")),
                ndf == null ? null : text(ndf, "fixingSource"),
                text(t, "book"), text(t, "trader"));
    }

    private static org.w3c.dom.Document parseSecure(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // XXE hardening
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (SAXException e) {
            throw new XmlRejectedException("Malformed XML: " + e.getMessage());
        } catch (ParserConfigurationException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Element child(Element parent, String name) {
        NodeList list = parent.getElementsByTagNameNS(NS, name);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().trim();
    }

    public static class XmlRejectedException extends RuntimeException {
        public XmlRejectedException(String message) {
            super(message);
        }
    }
}
