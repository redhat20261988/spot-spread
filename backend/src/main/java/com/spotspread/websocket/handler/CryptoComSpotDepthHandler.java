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

/**
 * Crypto.com 现货 book 频道，提取买一/卖一。
 * wss://stream.crypto.com/exchange/v1/market
 */
public class CryptoComSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://stream.crypto.com/exchange/v1/market";
    private static final Logger log = LoggerFactory.getLogger(CryptoComSpotDepthHandler.class);

    private static final String[] INSTRUMENTS = {"BTC_USDT", "ETH_USDT", "SOL_USDT", "XRP_USDT", "HYPE_USDT", "BNB_USDT"};
    private static final Map<String, String> INSTRUMENT_TO_SYMBOL = new HashMap<>();
    static {
        for (String inst : INSTRUMENTS) {
            INSTRUMENT_TO_SYMBOL.put(inst, inst.replace("_", ""));
        }
    }

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();
    private volatile ManagedWebSocket clientRef;

    public CryptoComSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("cryptocom", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        clientRef = client;
        log.info("Crypto.com spot depth WebSocket connected");
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                if (clientRef != null && clientRef.isOpen()) {
                    // Crypto.com 只支持 depth 10 或 50，不支持 1
                    StringBuilder channels = new StringBuilder("[");
                    for (int i = 0; i < INSTRUMENTS.length; i++) {
                        if (i > 0) channels.append(",");
                        channels.append("\"book.").append(INSTRUMENTS[i]).append(".10\"");
                    }
                    channels.append("]");
                    clientRef.send("{\"id\":1,\"method\":\"subscribe\",\"params\":{\"channels\":" + channels + "},\"nonce\":" + System.currentTimeMillis() + "}");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void onClosed(int code, String reason, boolean remote) {
        clientRef = null;
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            if (root.has("method") && "public/heartbeat".equals(root.path("method").asText(""))) {
                Long id = root.has("id") ? root.path("id").asLong() : null;
                if (id != null && clientRef != null && clientRef.isOpen()) {
                    clientRef.send("{\"id\":" + id + ",\"method\":\"public/respond-heartbeat\"}");
                }
                return;
            }
            // 订阅响应与增量更新在 result 中；部分推送可能在 params 中
            JsonNode payload = root.path("result");
            if (payload.isMissingNode()) {
                payload = root.path("params");
            }
            if (payload.isMissingNode()) return;
            JsonNode data = payload.path("data");
            if (!data.isArray() || data.isEmpty()) return;
            String instrumentName = payload.path("instrument_name").asText("");
            String symbol = INSTRUMENT_TO_SYMBOL.get(instrumentName);
            if (symbol == null) return;
            JsonNode item = data.get(0);
            JsonNode bids = item.path("bids");
            JsonNode asks = item.path("asks");
            if (bids.isMissingNode() && item.has("update")) {
                JsonNode upd = item.path("update");
                bids = upd.path("bids");
                asks = upd.path("asks");
            }
            BigDecimal bid1 = parseBest(bids, 0);
            BigDecimal ask1 = parseBest(asks, 0);
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("cryptocom", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("Crypto.com book parse error: {}", e.getMessage());
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
