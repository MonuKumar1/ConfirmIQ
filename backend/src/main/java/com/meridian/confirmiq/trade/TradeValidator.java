package com.meridian.confirmiq.trade;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Business validation of FX economics beyond simple field constraints. */
public final class TradeValidator {

    /** Relative tolerance between quoted rate and amount ratio (covers rounding of amounts). */
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("0.001");

    private TradeValidator() {}

    public static List<String> validate(EconomicTerms t, ProductType product) {
        List<String> errors = new ArrayList<>();
        if (t.buyCurrency().equals(t.sellCurrency())) {
            errors.add("Buy and sell currencies must differ");
        }
        if (t.valueDate().isBefore(t.tradeDate())) {
            errors.add("Value date cannot be before trade date");
        }
        if (isWeekend(t.valueDate())) {
            errors.add("Value date " + t.valueDate() + " falls on a weekend");
        }
        checkRate("Near leg", t.buyCurrency(), t.buyAmount(), t.sellCurrency(), t.sellAmount(), t.rate(), errors);

        switch (product) {
            case FX_SPOT -> {
                if (businessDaysBetween(t.tradeDate(), t.valueDate()) > 2) {
                    errors.add("Spot value date must be no later than T+2");
                }
            }
            case FX_FORWARD -> {
                if (businessDaysBetween(t.tradeDate(), t.valueDate()) <= 2) {
                    errors.add("Forward value date must be after the spot date (T+2)");
                }
            }
            case FX_SWAP -> {
                if (t.farValueDate() == null || t.farBuyAmount() == null || t.farSellAmount() == null || t.farRate() == null) {
                    errors.add("FX swap requires far value date, far amounts and far rate");
                } else {
                    if (!t.farValueDate().isAfter(t.valueDate())) {
                        errors.add("Far value date must be after near value date");
                    }
                    if (isWeekend(t.farValueDate())) {
                        errors.add("Far value date " + t.farValueDate() + " falls on a weekend");
                    }
                    // Far leg reverses the near leg: we buy back what we sold.
                    checkRate("Far leg", t.sellCurrency(), t.farBuyAmount(), t.buyCurrency(), t.farSellAmount(),
                            t.farRate(), errors);
                }
            }
            case FX_NDF -> {
                if (t.settlementCurrency() == null || t.fixingDate() == null || t.fixingSource() == null) {
                    errors.add("NDF requires settlement currency, fixing date and fixing source");
                } else {
                    if (!t.settlementCurrency().equals(t.buyCurrency()) && !t.settlementCurrency().equals(t.sellCurrency())) {
                        errors.add("NDF settlement currency must be one of the traded currencies");
                    }
                    if (!t.fixingDate().isBefore(t.valueDate())) {
                        errors.add("NDF fixing date must be before the settlement date");
                    }
                }
            }
        }
        return errors;
    }

    private static void checkRate(String leg, String buyCcy, BigDecimal buyAmt, String sellCcy, BigDecimal sellAmt,
                                  BigDecimal rate, List<String> errors) {
        CurrencyPair pair = CurrencyPair.of(buyCcy, sellCcy);
        BigDecimal baseAmt = pair.base().equals(buyCcy) ? buyAmt : sellAmt;
        BigDecimal quoteAmt = pair.base().equals(buyCcy) ? sellAmt : buyAmt;
        BigDecimal implied = quoteAmt.divide(baseAmt, MathContext.DECIMAL64);
        BigDecimal deviation = implied.subtract(rate).abs().divide(rate, MathContext.DECIMAL64);
        if (deviation.compareTo(RATE_TOLERANCE) > 0) {
            errors.add("%s amounts imply %s %s but rate is %s".formatted(
                    leg, pair.code(), implied.round(new MathContext(8)).toPlainString(), rate.toPlainString()));
        }
    }

    static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    static int businessDaysBetween(LocalDate from, LocalDate to) {
        int days = 0;
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            if (!isWeekend(d.plusDays(1))) {
                days++;
            }
        }
        return days;
    }

    public static LocalDate addBusinessDays(LocalDate from, int days) {
        LocalDate d = from;
        int added = 0;
        while (added < days) {
            d = d.plusDays(1);
            if (!isWeekend(d)) {
                added++;
            }
        }
        return d;
    }

    /** Economic fields subject to validation; built from a request or an amendment. */
    public record EconomicTerms(LocalDate tradeDate, LocalDate valueDate,
                                String buyCurrency, BigDecimal buyAmount,
                                String sellCurrency, BigDecimal sellAmount, BigDecimal rate,
                                LocalDate farValueDate, BigDecimal farBuyAmount, BigDecimal farSellAmount,
                                BigDecimal farRate, String settlementCurrency, LocalDate fixingDate,
                                String fixingSource) {

        public static EconomicTerms of(TradeRequest r) {
            return new EconomicTerms(r.tradeDate(), r.valueDate(), r.buyCurrency(), r.buyAmount(),
                    r.sellCurrency(), r.sellAmount(), r.rate(), r.farValueDate(), r.farBuyAmount(),
                    r.farSellAmount(), r.farRate(), blankToNull(r.settlementCurrency()), r.fixingDate(),
                    blankToNull(r.fixingSource()));
        }

        public static EconomicTerms of(Trade t) {
            return new EconomicTerms(t.getTradeDate(), t.getValueDate(), t.getBuyCurrency(), t.getBuyAmount(),
                    t.getSellCurrency(), t.getSellAmount(), t.getRate(), t.getFarValueDate(), t.getFarBuyAmount(),
                    t.getFarSellAmount(), t.getFarRate(), t.getSettlementCurrency(), t.getFixingDate(),
                    t.getFixingSource());
        }

        private static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s;
        }
    }
}
