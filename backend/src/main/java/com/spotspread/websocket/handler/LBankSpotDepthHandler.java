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
import java.util.Map;

/**
 * LBank 现货深度 depth，提取买一/卖一。
 * wss://www.lbkex.net/ws/V2/ 公开 depth 订阅
 */
public class LBankSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://www.lbkex.net/ws/V2/";
    private static final Logger log = LoggerFactory.getLogger(LBankSpotDepthHandler.class);

    private static final Map<String, String> PAIR_TO_SYMBOL = Map.of(
            "btc_usdt", "BTCUSDT", "eth_usdt", "ETHUSDT", "sol_usdt", "SOLUSDT",
            "xrp_usdt", "XRPUSDT", "hype_usdt", "HYPEUSDT", "bnb_usdt", "BNBUSDT"
    );

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    public LBankSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("lbank", URI.create(WS_URL), this);
    }

    /** LBank 服务端约 6 分钟空闲会主动断开，需定期发送 ping 保活 */
    private static final String PING_MSG = "ping";

    @Override
    public String getHeartbeatMessage() {
        return PING_MSG;
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return 30_000;
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("LBank spot depth WebSocket connected");
        for (String pair : PAIR_TO_SYMBOL.keySet()) {
            String sub = "{\"action\":\"subscribe\",\"subscribe\":\"depth\",\"depth\":\"5\",\"pair\":\"" + pair + "\"}";
            client.send(sub);
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            String action = root.path("action").asText("");
            String pair = root.path("pair").asText("").toLowerCase();
            String symbol = PAIR_TO_SYMBOL.get(pair);
            if ("pong".equals(action) || "ping".equals(action)) {
                log.info("[LBank] 收到心跳响应 action={} raw={}", action, message.length() > 100 ? message.substring(0, 100) + "..." : message);
                return;
            }
            if (symbol == null) return;
            JsonNode depth = root.path("depth");
            if (depth.isMissingNode()) return;
            BigDecimal bid1 = parseBest(depth.path("bids"), 0);
            BigDecimal ask1 = parseBest(depth.path("asks"), 0);
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("lbank", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("[LBank] depth 解析失败 msg={} err={}", message.length() > 150 ? message.substring(0, 150) + "..." : message, e.getMessage());
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
