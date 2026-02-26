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
 * CoinEx 现货 depth.subscribe，提取买一/卖一。
 * wss://socket.coinex.com/v2/spot
 */
public class CoinExSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://socket.coinex.com/v2/spot";
    private static final Logger log = LoggerFactory.getLogger(CoinExSpotDepthHandler.class);

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    private static final String[] MARKETS = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "HYPEUSDT", "BNBUSDT"};

    public CoinExSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("coinex", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("CoinEx spot depth WebSocket connected");
        for (String market : MARKETS) {
            String sub = "{\"id\":1,\"method\":\"depth.subscribe\",\"params\":[\"" + market + "\",5,\"0\"]}";
            client.send(sub);
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            String method = root.path("method").asText("");
            if (!"depth.update".equals(method)) return;
            JsonNode params = root.path("params");
            if (!params.isArray() || params.size() < 2) return;
            String symbol = params.get(0).asText();
            JsonNode data = params.get(1);
            BigDecimal bid1 = parseBest(data.path("bids"), 0);
            BigDecimal ask1 = parseBest(data.path("asks"), 0);
            if (bid1 != null && ask1 != null) cache.updateBidAsk("coinex", symbol, bid1, ask1);
        } catch (Exception e) {
            log.warn("CoinEx spot depth parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parseBest(JsonNode arr, int idx) {
        if (!arr.isArray() || arr.size() <= idx) return null;
        JsonNode level = arr.get(idx);
        if (!level.isArray() || level.size() < 1) return null;
        try { return new BigDecimal(level.get(0).asText()); } catch (Exception e) { return null; }
    }
}
