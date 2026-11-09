package com.meridian.confirmiq.inbound;

import com.meridian.confirmiq.product.TermField;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Label-driven extraction of economic terms from confirmation text. Each expected term is
 * located by its printed label at the start of a line; the remainder of the line is the value.
 * Both house and counterparty label sets are tried and the more complete reading wins.
 */
@Component
public class PdfTermExtractor {

    private static final Pattern TRADE_REF = Pattern.compile("MC-FX-\\d{4}-\\d{6}");

    public String text(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new IllegalArgumentException("The file is not a readable PDF", e);
        }
    }

    /** Finds our trade reference: "Your Reference" line on counterparty paper, else any reference in the text. */
    public Optional<String> findTradeRef(String text) {
        for (String line : lines(text)) {
            if (line.startsWith("Your Reference")) {
                Matcher m = TRADE_REF.matcher(line);
                if (m.find()) {
                    return Optional.of(m.group());
                }
            }
        }
        Matcher m = TRADE_REF.matcher(text);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    public Extraction extract(String text, List<TermField> expected) {
        List<String> lines = lines(text);
        Map<String, String> house = read(lines, expected, false);
        Map<String, String> cpty = read(lines, expected, true);
        return cpty.size() > house.size() ? new Extraction(cpty, true) : new Extraction(house, false);
    }

    private static Map<String, String> read(List<String> lines, List<TermField> expected, boolean counterpartyPaper) {
        // Longest labels first so "Far Leg We Sell" is not consumed by a shorter label.
        List<TermField> ordered = new ArrayList<>(expected);
        ordered.sort(Comparator.comparingInt((TermField f) -> label(f, counterpartyPaper).length()).reversed());
        Map<String, String> values = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        for (TermField f : ordered) {
            String label = label(f, counterpartyPaper);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!used.contains(i) && line.startsWith(label + " ") && line.length() > label.length() + 1) {
                    values.put(f.key(), line.substring(label.length()).replaceFirst("^[\\s:]+", "").trim());
                    used.add(i);
                    break;
                }
            }
        }
        return values;
    }

    private static String label(TermField f, boolean counterpartyPaper) {
        return counterpartyPaper ? f.counterpartyLabel() : f.label();
    }

    private static List<String> lines(String text) {
        return Arrays.stream(text.split("\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public record Extraction(Map<String, String> values, boolean counterpartyPaper) {}
}
