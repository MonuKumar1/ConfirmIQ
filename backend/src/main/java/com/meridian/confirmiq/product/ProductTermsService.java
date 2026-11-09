package com.meridian.confirmiq.product;

import com.meridian.confirmiq.common.Hashing;
import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.trade.Trade;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductTermsService {

    private final List<ProductTermsProvider> providers;
    private final String houseName;

    public ProductTermsService(List<ProductTermsProvider> providers, AppProperties props) {
        this.providers = providers;
        this.houseName = props.bank().shortName();
    }

    public List<TermField> termsOf(Trade trade) {
        return providers.stream()
                .filter(p -> p.supports(trade.getProductType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No terms provider for " + trade.getProductType()))
                .economicTerms(trade, houseName);
    }

    /** Fingerprint of the economic terms; embedded in the document QR to detect stale or altered paper. */
    public String termsHash(Trade trade) {
        return Hashing.sha256(termsOf(trade).stream()
                .map(f -> f.key() + "=" + f.value())
                .collect(Collectors.joining("|")));
    }
}
