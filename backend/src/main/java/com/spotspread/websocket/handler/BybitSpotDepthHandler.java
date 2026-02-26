package com.spotspread.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotspread.event.InfluxDbMessagePublisher;
import com.spotspread.service.OrderBookCacheService;
import com.spotspread.websocket.ExchangeWebSocketHandler;
import com.spotspread.websocket.ManagedWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;

/**
 * Bybit 现货订单簿 orderbook.1，提取买一/卖一。
 * wss://stream.bybit.com/v5/public/spot
 */
public class BybitSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://stream.bybit.com/v5/public/spot";
    private static final Logger log = LoggerFactory.getLogger(BybitSpotDepthHandler.class);

    private static final String[] SYMBOLS = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "HYPEUSDT", "BNBUSDT"};

    private final OrderBookCacheService cache;
    private final InfluxDbMessagePublisher influxPublisher;
    private final ObjectMapper om = new ObjectMapper();

    public BybitSpotDepthHandler(OrderBookCacheService cache, InfluxDbMessagePublisher influxPublisher) {
        this.cache = cache;
        this.influxPublisher = influxPublisher;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("bybit", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Bybit spot depth WebSocket connected");
        for (String sym : SYMBOLS) {
            client.send("{\"op\":\"subscribe\",\"args\":[\"orderbook.1." + sym + "\"]}");
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            JsonNode topic = root.path("topic");
            if (topic.isMissingNode()) return;
            String topicStr = topic.asText("");
            if (!topicStr.startsWith("orderbook.")) return;
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return;
            String symbol = data.path("s").asText("");
            BigDecimal bid1 = parseBest(data.path("b"), 0);
            BigDecimal ask1 = parseBest(data.path("a"), 0);
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("bybit", symbol, bid1, ask1);
                long exchangeTs = parseTimestamp(root.path("ts"));
                if (exchangeTs > 0) {
                    long latencyMs = System.currentTimeMillis() - exchangeTs;
                    influxPublisher.publishPriceLatency("bybit", symbol, latencyMs);
                }
            }
        } catch (Exception e) {
            log.warn("Bybit orderbook parse error: {}", e.getMessage());
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

    private long parseTimestamp(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return 0;
        try {
            return n.asLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
