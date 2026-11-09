package com.meridian.confirmiq.product;

import com.meridian.confirmiq.product.TermField.MatchType;
import com.meridian.confirmiq.trade.ProductType;
import com.meridian.confirmiq.trade.Trade;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.meridian.confirmiq.product.TermFormat.*;

@Component
public class FxTermsProvider implements ProductTermsProvider {

    @Override
    public boolean supports(ProductType productType) {
        return "FX".equals(productType.getAssetClass());
    }

    @Override
    public List<TermField> economicTerms(Trade t, String house) {
        boolean swap = t.getProductType() == ProductType.FX_SWAP;
        List<TermField> f = new ArrayList<>();
        f.add(new TermField("tradeRef", "Our Reference", "Your Reference", t.getTradeRef(), MatchType.TEXT));
        f.add(new TermField("productType", "Transaction Type", "Transaction Type",
                t.getProductType().getDisplayName(), MatchType.TEXT));
        f.add(new TermField("tradeDate", "Trade Date", "Trade Date", date(t.getTradeDate()), MatchType.DATE));
        f.add(new TermField("currencyPair", "Currency Pair", "Currency Pair", t.currencyPair(), MatchType.TEXT));
        f.add(new TermField("valueDate", swap ? "Near Value Date" : "Value Date",
                swap ? "Near Value Date" : "Value Date", date(t.getValueDate()), MatchType.DATE));
        f.add(new TermField("buyAmount", (swap ? "Near Leg " : "") + house + " Buys",
                (swap ? "Near Leg " : "") + "We Sell", amount(t.getBuyCurrency(), t.getBuyAmount()), MatchType.AMOUNT));
        f.add(new TermField("sellAmount", (swap ? "Near Leg " : "") + house + " Sells",
                (swap ? "Near Leg " : "") + "We Buy", amount(t.getSellCurrency(), t.getSellAmount()), MatchType.AMOUNT));
        f.add(new TermField("rate", swap ? "Near Rate" : "Exchange Rate",
                swap ? "Near Rate" : "Exchange Rate", rate(t.getRate()), MatchType.RATE));

        if (swap) {
            f.add(new TermField("farValueDate", "Far Value Date", "Far Value Date",
                    date(t.getFarValueDate()), MatchType.DATE));
            f.add(new TermField("farBuyAmount", "Far Leg " + house + " Buys", "Far Leg We Sell",
                    amount(t.getSellCurrency(), t.getFarBuyAmount()), MatchType.AMOUNT));
            f.add(new TermField("farSellAmount", "Far Leg " + house + " Sells", "Far Leg We Buy",
                    amount(t.getBuyCurrency(), t.getFarSellAmount()), MatchType.AMOUNT));
            f.add(new TermField("farRate", "Far Rate", "Far Rate", rate(t.getFarRate()), MatchType.RATE));
        }
        if (t.getProductType() == ProductType.FX_NDF) {
            f.add(new TermField("settlementCurrency", "Settlement Currency", "Settlement Currency",
                    t.getSettlementCurrency(), MatchType.TEXT));
            f.add(new TermField("fixingDate", "Fixing Date", "Fixing Date", date(t.getFixingDate()), MatchType.DATE));
            f.add(new TermField("fixingSource", "Settlement Rate Option", "Settlement Rate Option",
                    t.getFixingSource(), MatchType.TEXT));
        }
        return f;
    }
}
