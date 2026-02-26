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
 * Gate.io 现货 book_ticker，提取买一/卖一。
 * wss://api.gateio.ws/ws/v4/
 */
public class GateSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://api.gateio.ws/ws/v4/";
    private static final Logger log = LoggerFactory.getLogger(GateSpotDepthHandler.class);

    private static final String[] PAIRS = {"BTC_USDT", "ETH_USDT", "SOL_USDT", "XRP_USDT", "HYPE_USDT", "BNB_USDT"};

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public GateSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("gateio", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Gate.io spot depth WebSocket connected");
        long time = System.currentTimeMillis() / 1000;
        StringBuilder payload = new StringBuilder("[");
        for (int i = 0; i < PAIRS.length; i++) {
            if (i > 0) payload.append(",");
            payload.append("\"").append(PAIRS[i]).append("\"");
        }
        payload.append("]");
        String msg = String.format("{\"time\":%d,\"channel\":\"spot.book_ticker\",\"event\":\"subscribe\",\"payload\":%s}", time, payload);
        client.send(msg);
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            if (!"update".equals(root.path("event").asText(""))) return;
            if (!"spot.book_ticker".equals(root.path("channel").asText(""))) return;
            JsonNode result = root.path("result");
            if (result.isMissingNode()) return;
            String s = result.path("s").asText("");
            String symbol = s.replace("_", "");
            BigDecimal bid1 = parseDecimal(result.path("b"));
            BigDecimal ask1 = parseDecimal(result.path("a"));
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("gateio", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("Gate.io book_ticker parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parseDecimal(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
