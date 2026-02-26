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
 * Bitget 现货 books5，提取买一/卖一。
 * wss://ws.bitget.com/v2/ws/public
 */
public class BitgetSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://ws.bitget.com/v2/ws/public";
    private static final Logger log = LoggerFactory.getLogger(BitgetSpotDepthHandler.class);

    private static final String[] SYMBOLS = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "HYPEUSDT", "BNBUSDT"};

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public BitgetSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("bitget", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Bitget spot depth WebSocket connected");
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < SYMBOLS.length; i++) {
            if (i > 0) args.append(",");
            args.append("{\"instType\":\"SPOT\",\"channel\":\"books5\",\"instId\":\"").append(SYMBOLS[i]).append("\"}");
        }
        client.send("{\"op\":\"subscribe\",\"args\":[" + args + "]}");
    }

    @Override
    public String getHeartbeatMessage() {
        return "ping";
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return 25_000;
    }

    @Override
    public void onMessage(String message) {
        if (message == null || "pong".equals(message)) return;
        try {
            JsonNode root = om.readTree(message);
            if (root.has("event")) {
                String ev = root.path("event").asText("");
                if ("error".equals(ev) || "pong".equals(ev)) return;
            }
            JsonNode arg = root.path("arg");
            if (arg.isMissingNode()) return;
            String channel = arg.path("channel").asText("");
            if (!"books5".equals(channel)) return;
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) return;
            String instIdFromArg = arg.path("instId").asText("");
            for (JsonNode item : data) {
                String instId = item.has("instId") ? item.path("instId").asText("") : instIdFromArg;
                if (instId.isEmpty()) instId = instIdFromArg;
                BigDecimal bid1 = parseBest(item.path("bids"), 0);
                BigDecimal ask1 = parseBest(item.path("asks"), 0);
                if (bid1 != null && ask1 != null && !instId.isEmpty()) {
                    cache.updateBidAsk("bitget", instId, bid1, ask1);
                }
            }
        } catch (Exception e) {
            log.warn("Bitget books5 parse error: {}", e.getMessage());
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
