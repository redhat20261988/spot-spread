package com.spotspread.controller;

import com.spotspread.config.ExchangeFeeRates;
import com.spotspread.service.OrderBookCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExchangePriceController {

    private static final List<String> EXCHANGES = List.of(
            "binance", "bitfinex", "coinex", "okx", "bybit", "gateio", "bitget", "lbank", "whitebit",
            "bitunix", "cryptocom"
    );
    private static final String DEFAULT_SYMBOL = "BTCUSDT";

    private final OrderBookCacheService cache;

    public ExchangePriceController(OrderBookCacheService cache) {
        this.cache = cache;
    }

    @GetMapping("/exchange-prices")
    public ResponseEntity<Map<String, Object>> getExchangePrices(
            @RequestParam(defaultValue = "BTC") String symbol) {
        String sym = symbol.toUpperCase() + "USDT";
        List<ExchangePriceDto> exchanges = new ArrayList<>();
        for (String ex : EXCHANGES) {
            var bidAsk = cache.getBidAsk(ex, sym);
            BigDecimal makerPct = ExchangeFeeRates.getSpotMakerFeePct(ex);
            BigDecimal takerPct = ExchangeFeeRates.getSpotTakerFeePct(ex);
            if (makerPct == null || takerPct == null) continue;
            BigDecimal bid1 = null;
            BigDecimal ask1 = null;
            BigDecimal spotPrice = null;
            if (bidAsk != null && bidAsk.bid1() != null && bidAsk.ask1() != null) {
                bid1 = bidAsk.bid1();
                ask1 = bidAsk.ask1();
                spotPrice = bid1.add(ask1).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            }
            exchanges.add(new ExchangePriceDto(ex, spotPrice, bid1, ask1,
                    takerPct.doubleValue(), makerPct.doubleValue()));
        }
        return ResponseEntity.ok(Map.of("exchanges", exchanges, "symbol", symbol));
    }

    public record ExchangePriceDto(String exchange, BigDecimal spotPrice, BigDecimal bid1, BigDecimal ask1,
                                   double takerFeePct, double makerFeePct) {}
}
