package com.spotspread.config;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 各交易所现货手续费率（Maker/Taker），用于价差套利「一 maker 一 taker 且总手续费最小」。
 */
public final class ExchangeFeeRates {

    private static final Map<String, BigDecimal> SPOT_MAKER_PCT = Map.ofEntries(
            Map.entry("binance", new BigDecimal("0.1")),
            Map.entry("mexc", BigDecimal.ZERO),
            Map.entry("coinex", new BigDecimal("0.16")),
            Map.entry("bitfinex", new BigDecimal("0.1")),
            Map.entry("whitebit", new BigDecimal("0.1")),
            Map.entry("bingx", new BigDecimal("0.1")),
            Map.entry("coinw", new BigDecimal("0.1")),
            Map.entry("bitunix", new BigDecimal("0.1")),
            Map.entry("lbank", new BigDecimal("0.1")),
            Map.entry("hyperliquid", new BigDecimal("0.04")),
            Map.entry("dydx", new BigDecimal("0.01"))
    );

    private static final Map<String, BigDecimal> SPOT_TAKER_PCT = Map.ofEntries(
            Map.entry("binance", new BigDecimal("0.1")),
            Map.entry("mexc", new BigDecimal("0.05")),
            Map.entry("coinex", new BigDecimal("0.16")),
            Map.entry("bitfinex", new BigDecimal("0.15")),
            Map.entry("whitebit", new BigDecimal("0.1")),
            Map.entry("bingx", new BigDecimal("0.1")),
            Map.entry("coinw", new BigDecimal("0.1")),
            Map.entry("bitunix", new BigDecimal("0.1")),
            Map.entry("lbank", new BigDecimal("0.1")),
            Map.entry("hyperliquid", new BigDecimal("0.07")),
            Map.entry("dydx", new BigDecimal("0.05"))
    );

    /** 现货 Maker 费率（%），无则返回 null */
    public static BigDecimal getSpotMakerFeePct(String exchange) {
        return SPOT_MAKER_PCT.get(exchange != null ? exchange.toLowerCase() : "");
    }

    /** 现货 Taker 费率（%），无则返回 null */
    public static BigDecimal getSpotTakerFeePct(String exchange) {
        return SPOT_TAKER_PCT.get(exchange != null ? exchange.toLowerCase() : "");
    }

    private ExchangeFeeRates() {}
}
