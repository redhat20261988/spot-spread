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
 * OKX 现货订单簿 books5，提取买一/卖一。
 * wss://ws.okx.com:8443/ws/v5/public
 */
public class OkxSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://ws.okx.com:8443/ws/v5/public";
    private static final Logger log = LoggerFactory.getLogger(OkxSpotDepthHandler.class);

    private static final String[] SPOT_INST_IDS = {"BTC-USDT", "ETH-USDT", "SOL-USDT", "XRP-USDT", "HYPE-USDT", "BNB-USDT"};

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public OkxSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("okx", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("OKX spot depth WebSocket connected");
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < SPOT_INST_IDS.length; i++) {
            if (i > 0) args.append(",");
            args.append("{\"channel\":\"books5\",\"instId\":\"").append(SPOT_INST_IDS[i]).append("\"}");
        }
        client.send("{\"op\":\"subscribe\",\"args\":[" + args + "]}");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            if (root.has("event") && !"subscribe".equals(root.path("event").asText(""))) return;
            JsonNode arg = root.path("arg");
            if (arg.isMissingNode()) return;
            String channel = arg.path("channel").asText("");
            if (!"books5".equals(channel)) return;
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) return;
            JsonNode item = data.get(0);
            String instId = item.path("instId").asText("");
            String symbol = instId.replace("-", "");
            BigDecimal bid1 = parseBest(item.path("bids"), 0);
            BigDecimal ask1 = parseBest(item.path("asks"), 0);
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("okx", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("OKX books5 parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parseBest(JsonNode arr, int idx) {
        if (!arr.isArray() || arr.size() <= idx) return null;
        JsonNode level = arr.get(idx);
        if (!level.isArray() || level.size() < 1) return null;
        try {
            return new BigDecimal(level.get(0).asText());
        } catch (Exception e) {
            return null;
        }
    }
}
