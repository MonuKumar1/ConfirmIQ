package com.meridian.confirmiq.trade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductType {
    FX_SPOT("FX", "FX Spot"),
    FX_FORWARD("FX", "FX Outright Forward"),
    FX_SWAP("FX", "FX Swap"),
    FX_NDF("FX", "Non-Deliverable Forward");

    private final String assetClass;
    private final String displayName;
}
