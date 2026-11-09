package com.meridian.confirmiq.inbound;

import com.meridian.confirmiq.product.TermField;
import com.meridian.confirmiq.product.TermFormat;
import com.meridian.confirmiq.product.TermFormat.ParsedAmount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Field-by-field comparison of our booked terms against terms read from the client's document. */
@Component
public class MatchingEngine {

    static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.000001");

    public List<FieldResult> compare(List<TermField> expected, Map<String, String> received) {
        List<FieldResult> results = new ArrayList<>();
        for (TermField f : expected) {
            String actual = received.get(f.key());
            if (actual == null) {
                results.add(new FieldResult(f.key(), f.label(), f.value(), null, FieldStatus.MISSING, null));
                continue;
            }
            try {
                boolean ok = switch (f.type()) {
                    case TEXT -> normalize(f.value()).equals(normalize(actual));
                    case DATE -> TermFormat.parseDate(f.value()).equals(TermFormat.parseDate(actual));
                    case AMOUNT -> amountsMatch(TermFormat.parseAmount(f.value()), TermFormat.parseAmount(actual));
                    case RATE -> TermFormat.parseRate(f.value()).subtract(TermFormat.parseRate(actual)).abs()
                            .compareTo(RATE_TOLERANCE) <= 0;
                };
                results.add(new FieldResult(f.key(), f.label(), f.value(), actual,
                        ok ? FieldStatus.MATCH : FieldStatus.MISMATCH, ok ? null : difference(f, actual)));
            } catch (RuntimeException e) {
                results.add(new FieldResult(f.key(), f.label(), f.value(), actual, FieldStatus.UNREADABLE,
                        "Value could not be interpreted"));
            }
        }
        return results;
    }

    public static boolean allMatch(List<FieldResult> results) {
        return results.stream().allMatch(r -> r.status() == FieldStatus.MATCH);
    }

    private static boolean amountsMatch(ParsedAmount ours, ParsedAmount theirs) {
        return ours.currency().equals(theirs.currency())
                && ours.amount().subtract(theirs.amount()).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    private static String difference(TermField f, String actual) {
        try {
            return switch (f.type()) {
                case AMOUNT -> {
                    ParsedAmount a = TermFormat.parseAmount(f.value());
                    ParsedAmount b = TermFormat.parseAmount(actual);
                    yield a.currency().equals(b.currency())
                            ? "Δ " + b.amount().subtract(a.amount()).toPlainString() + " " + a.currency()
                            : "Currency differs";
                }
                case RATE -> "Δ " + TermFormat.parseRate(actual).subtract(TermFormat.parseRate(f.value())).toPlainString();
                case DATE -> "Δ " + java.time.temporal.ChronoUnit.DAYS.between(
                        TermFormat.parseDate(f.value()), TermFormat.parseDate(actual)) + " day(s)";
                case TEXT -> "Text differs";
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    public enum FieldStatus { MATCH, MISMATCH, MISSING, UNREADABLE }

    public record FieldResult(String key, String label, String expected, String actual, FieldStatus status,
                              String difference) {}
}
