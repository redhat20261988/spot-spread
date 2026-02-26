package com.spotspread.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotspread.service.OrderBookCacheService;
import com.spotspread.websocket.ExchangeWebSocketHandler;
import com.spotspread.websocket.ManagedWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Binance 现货深度 depth5，提取买一/卖一。
 */
public class BinanceSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@depth5/ethusdt@depth5/solusdt@depth5/xrpusdt@depth5/hypeusdt@depth5/bnbusdt@depth5";
    private static final Logger log = LoggerFactory.getLogger(BinanceSpotDepthHandler.class);

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public BinanceSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("binance", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Binance spot depth WebSocket connected");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            JsonNode stream = root.get("stream");
            if (stream == null) return;
            String streamName = stream.asText();
            String symbol = streamName.contains("@") ? streamName.substring(0, streamName.indexOf("@")).toUpperCase() + "USDT" : null;
            if (symbol == null) return;
            JsonNode data = root.path("data");
            BigDecimal bid1 = parseBest(data.path("bids"), 0);
            BigDecimal ask1 = parseBest(data.path("asks"), 0);
            if (bid1 != null && ask1 != null) cache.updateBidAsk("binance", symbol, bid1, ask1);
        } catch (Exception e) {
            log.warn("Binance spot depth parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parseBest(JsonNode arr, int idx) {
        if (!arr.isArray() || arr.size() <= idx) return null;
        JsonNode level = arr.get(idx);
        if (!level.isArray() || level.size() < 1) return null;
        try {
            return new BigDecimal(level.get(0).asText());
        } catch (Exception e) { return null; }
    }
}
