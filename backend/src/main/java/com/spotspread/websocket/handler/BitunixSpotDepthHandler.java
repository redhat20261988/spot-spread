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
 * Bitunix 现货深度 depth_book1，提取买一/卖一。
 * wss://fapi.bitunix.com/public/
 */
public class BitunixSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://fapi.bitunix.com/public/";
    private static final Logger log = LoggerFactory.getLogger(BitunixSpotDepthHandler.class);

    private static final String[] SYMBOLS = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "HYPEUSDT", "BNBUSDT"};

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public BitunixSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("bitunix", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Bitunix spot depth WebSocket connected");
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < SYMBOLS.length; i++) {
            if (i > 0) args.append(",");
            args.append("{\"symbol\":\"").append(SYMBOLS[i]).append("\",\"ch\":\"depth_book1\"}");
        }
        client.send("{\"op\":\"subscribe\",\"args\":[" + args + "]}");
    }

    @Override
    public String getHeartbeatMessage() {
        return "{\"op\":\"ping\",\"ping\":" + (System.currentTimeMillis() / 1000) + "}";
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return 25_000;
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            String op = root.path("op").asText("");
            if ("ping".equals(op)) return;
            if (!"depth_book1".equals(root.path("ch").asText(""))) return;
            String symbol = root.path("symbol").asText("");
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return;
            BigDecimal bid1 = parseBest(data.path("b"), 0);
            BigDecimal ask1 = parseBest(data.path("a"), 0);
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("bitunix", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("Bitunix depth parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parseBest(JsonNode arr, int idx) {
        if (!arr.isArray() || arr.size() <= idx) return null;
        JsonNode level = arr.get(idx);
        if (level.isArray() && level.size() >= 1) {
            try {
                return new BigDecimal(level.get(0).asText());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
