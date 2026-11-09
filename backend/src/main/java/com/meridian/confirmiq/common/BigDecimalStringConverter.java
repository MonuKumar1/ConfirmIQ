package com.meridian.confirmiq.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/** Persists BigDecimal as an exact decimal string; SQLite would otherwise store REAL. */
@Converter(autoApply = true)
public class BigDecimalStringConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
