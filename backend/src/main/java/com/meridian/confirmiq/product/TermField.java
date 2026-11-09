package com.meridian.confirmiq.product;

/**
 * One economic term of a confirmation.
 *
 * @param key               stable identifier used in match results
 * @param label             label printed on our (house) paper
 * @param counterpartyLabel label used on counterparty paper, which is written from their perspective
 * @param value             formatted display value, exactly as printed
 * @param type              comparison semantics for matching
 */
public record TermField(String key, String label, String counterpartyLabel, String value, MatchType type) {

    public enum MatchType {
        /** Case-insensitive exact text. */
        TEXT,
        /** Calendar date. */
        DATE,
        /** Currency + amount, tolerance 0.01. */
        AMOUNT,
        /** Exchange rate, tolerance 0.000001. */
        RATE
    }
}
