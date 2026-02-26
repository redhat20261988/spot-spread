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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bitfinex 现货订单簿 book channel len=1，提取买一/卖一。
 */
public class BitfinexSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://api-pub.bitfinex.com/ws/2";
    private static final Logger log = LoggerFactory.getLogger(BitfinexSpotDepthHandler.class);

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();
    private final Map<Integer, String> channelToSymbol = new HashMap<>();
    private final Map<String, BigDecimal[]> symbolBook = new ConcurrentHashMap<>();

    private static final Map<String, String> SYMBOL_MAP = Map.of(
            "tBTCUSD", "BTCUSDT", "tETHUSD", "ETHUSDT", "tSOLUSD", "SOLUSDT",
            "tXRPUSD", "XRPUSDT", "tHYPEUSD", "HYPEUSDT", "tBNBUSD", "BNBUSDT"
    );

    public BitfinexSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("bitfinex", URI.create(WS_URL), this);
    }

    private static final String PING_MSG = "{\"event\":\"ping\"}";

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Bitfinex spot depth WebSocket connected");
        for (String sym : SYMBOL_MAP.keySet()) {
            client.send("{\"event\":\"subscribe\",\"channel\":\"book\",\"symbol\":\"" + sym + "\",\"len\":\"1\"}");
        }
    }

    @Override
    public String getHeartbeatMessage() {
        return PING_MSG;
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return 30_000;
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            if (root.has("event") && "subscribed".equals(root.path("event").asText())) {
                int chanId = root.path("chanId").asInt();
                String symbol = root.path("symbol").asText();
                channelToSymbol.put(chanId, symbol);
                return;
            }
            if (!root.isArray() || root.size() < 2) return;
            int chanId = root.get(0).asInt();
            String symbolKey = channelToSymbol.get(chanId);
            if (symbolKey == null) return;
            String symbol = SYMBOL_MAP.get(symbolKey);
            if (symbol == null) return;
            BigDecimal[] book = symbolBook.computeIfAbsent(symbol, k -> new BigDecimal[]{null, null});
            JsonNode data = root.get(1);
            if (data.isArray()) {
                JsonNode first = data.get(0);
                if (first != null && first.isNumber()) {
                    double amount = root.size() > 3 ? root.get(3).asDouble() : 0;
                    BigDecimal price = BigDecimal.valueOf(first.asDouble());
                    if (amount > 0) book[0] = price; else if (amount < 0) book[1] = price;
                } else {
                    for (int i = 0; i < data.size(); i++) {
                        JsonNode entry = data.get(i);
                        BigDecimal price = parsePrice(entry);
                        if (price == null) continue;
                        double amount = parseAmount(entry);
                        if (amount > 0) book[0] = price;
                        else if (amount < 0) book[1] = price;
                    }
                }
            }
            if (book[0] != null && book[1] != null) cache.updateBidAsk("bitfinex", symbol, book[0], book[1]);
        } catch (Exception e) {
            log.warn("Bitfinex spot depth parse error: {}", e.getMessage());
        }
    }

    private BigDecimal parsePrice(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isArray() && n.size() > 0) {
            JsonNode first = n.get(0);
            if (first.isNumber()) return BigDecimal.valueOf(first.asDouble());
            return parseNum(first);
        }
        return n.isNumber() ? BigDecimal.valueOf(n.asDouble()) : null;
    }

    private double parseAmount(JsonNode n) {
        if (n == null || !n.isArray() || n.size() <= 2) return 0;
        try { return n.get(2).asDouble(); } catch (Exception e) { return 0; }
    }

    private BigDecimal parseNum(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try { return new BigDecimal(n.asText()); } catch (Exception e) { return null; }
    }
}
