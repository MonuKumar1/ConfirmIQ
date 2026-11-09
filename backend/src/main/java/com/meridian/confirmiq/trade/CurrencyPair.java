package com.meridian.confirmiq.trade;

import java.util.List;

/**
 * Market-convention currency pair. The base currency is the one ranked higher in the
 * standard quotation hierarchy (EUR > GBP > AUD > NZD > USD > CAD > CHF > JPY ...),
 * and rates are expressed as units of quote currency per one unit of base.
 */
public record CurrencyPair(String base, String quote) {

    private static final List<String> PRIORITY = List.of(
            "EUR", "GBP", "AUD", "NZD", "USD", "CAD", "CHF", "NOK", "SEK", "DKK",
            "SGD", "HKD", "CNH", "INR", "KRW", "TWD", "BRL", "ZAR", "MXN", "JPY");

    public static CurrencyPair of(String ccy1, String ccy2) {
        return rank(ccy1) <= rank(ccy2) ? new CurrencyPair(ccy1, ccy2) : new CurrencyPair(ccy2, ccy1);
    }

    private static int rank(String ccy) {
        int idx = PRIORITY.indexOf(ccy);
        return idx < 0 ? PRIORITY.size() : idx;
    }

    public String code() {
        return base + "/" + quote;
    }
}
