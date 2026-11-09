package com.meridian.confirmiq.product;

import com.meridian.confirmiq.trade.ProductType;
import com.meridian.confirmiq.trade.Trade;

import java.util.List;

/**
 * Strategy describing the confirmable economic terms of a product family.
 * Adding an asset class (rates, equities, ...) means adding one implementation.
 */
public interface ProductTermsProvider {

    boolean supports(ProductType productType);

    List<TermField> economicTerms(Trade trade, String houseName);
}
