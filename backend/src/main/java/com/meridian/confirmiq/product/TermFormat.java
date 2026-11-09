package com.meridian.confirmiq.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formatting and parsing of term values so printed documents can be read back for matching. */
public final class TermFormat {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final Set<String> ZERO_DECIMAL_CCY = Set.of("JPY", "KRW");
    private static final Pattern AMOUNT = Pattern.compile("([A-Z]{3})\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");

    private TermFormat() {}

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String amount(String ccy, BigDecimal amount) {
        int scale = ZERO_DECIMAL_CCY.contains(ccy) ? 0 : 2;
        DecimalFormat fmt = new DecimalFormat(scale == 0 ? "#,##0" : "#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return ccy + " " + fmt.format(amount.setScale(scale, RoundingMode.HALF_UP));
    }

    public static String rate(BigDecimal rate) {
        return rate.setScale(Math.max(rate.stripTrailingZeros().scale(), 4), RoundingMode.HALF_UP).toPlainString();
    }

    public static LocalDate parseDate(String text) {
        String s = text.trim();
        try {
            return LocalDate.parse(s, DATE);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(s);
        }
    }

    public static ParsedAmount parseAmount(String text) {
        Matcher m = AMOUNT.matcher(text.trim());
        if (!m.find()) {
            throw new IllegalArgumentException("Unreadable amount: " + text);
        }
        return new ParsedAmount(m.group(1), new BigDecimal(m.group(2).replace(",", "")));
    }

    public static BigDecimal parseRate(String text) {
        return new BigDecimal(text.trim().replace(",", "").split("\\s+")[0]);
    }

    public record ParsedAmount(String currency, BigDecimal amount) {}
}
