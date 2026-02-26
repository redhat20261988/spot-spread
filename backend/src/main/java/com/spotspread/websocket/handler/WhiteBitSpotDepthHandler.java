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
import java.util.Set;

/**
 * WhiteBIT 现货 bookTicker，提取买一/卖一。
 * wss://api.whitebit.com/ws
 */
public class WhiteBitSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://api.whitebit.com/ws";
    private static final Logger log = LoggerFactory.getLogger(WhiteBitSpotDepthHandler.class);

    private static final Set<String> VALID_SYMBOLS = Set.of("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "HYPEUSDT", "BNBUSDT");

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public WhiteBitSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("whitebit", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("WhiteBIT spot depth WebSocket connected");
        client.send("{\"id\":1,\"method\":\"bookTicker_subscribe\",\"params\":[]}");
    }

    @Override
    public String getHeartbeatMessage() {
        return "{\"id\":0,\"method\":\"ping\",\"params\":[]}";
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return 50_000;
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            if (!"bookTicker_update".equals(root.path("method").asText(""))) return;
            JsonNode params = root.path("params");
            if (!params.isArray() || params.isEmpty()) return;
            JsonNode data = params.get(0);
            if (!data.isArray() || data.size() < 8) return;
            String market = data.get(2).asText("");
            String symbol = market.replace("_", "");
            if (!VALID_SYMBOLS.contains(symbol)) return;
            BigDecimal bid1 = parseDecimal(data.get(4));
            BigDecimal ask1 = parseDecimal(data.get(6));
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("whitebit", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("WhiteBIT bookTicker parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parseDecimal(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
