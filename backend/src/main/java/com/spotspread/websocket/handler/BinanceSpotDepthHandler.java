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
 * Binance 现货买一/卖一，使用 @bookTicker 流。
 * 采用 /ws/ 原始流 + SUBSCRIBE，消息格式为 { "s":"BTCUSDT", "b":"...", "a":"..." }
 */
public class BinanceSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://stream.binance.com:443/ws";
    private static final Logger log = LoggerFactory.getLogger(BinanceSpotDepthHandler.class);

    private final OrderBookCacheService cache;
    private final InfluxDbMessagePublisher influxPublisher;
    private final ObjectMapper om = new ObjectMapper();

    public BinanceSpotDepthHandler(OrderBookCacheService cache, InfluxDbMessagePublisher influxPublisher) {
        this.cache = cache;
        this.influxPublisher = influxPublisher;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("binance", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("Binance spot depth WebSocket connected");
        String sub = "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@bookTicker\",\"ethusdt@bookTicker\",\"solusdt@bookTicker\",\"xrpusdt@bookTicker\",\"hypeusdt@bookTicker\",\"bnbusdt@bookTicker\"],\"id\":1}";
        client.send(sub);
    }

    /** Binance 使用 RFC 6455 协议层 ping/pong，超时 30 秒无消息则库自动发 ping */
    @Override
    public int getConnectionLostTimeoutSeconds() {
        return 30;
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            // 忽略订阅确认 { "result": null, "id": 1 }
            if (root.has("result")) return;
            // 原始流格式: { "s":"BTCUSDT", "b":"...", "a":"..." } 在根节点
            JsonNode sNode = root.path("s");
            if (sNode.isMissingNode() || sNode.isNull()) return;
            String symbol = sNode.asText();
            if (symbol.isEmpty()) return;
            // 兼容 combined 格式 data.b / data.a
            JsonNode data = root.path("data");
            JsonNode bidNode = data.isMissingNode() ? root.path("b") : data.path("b");
            JsonNode askNode = data.isMissingNode() ? root.path("a") : data.path("a");
            BigDecimal bid1 = parsePrice(bidNode);
            BigDecimal ask1 = parsePrice(askNode);
            if (bid1 != null && ask1 != null && bid1.compareTo(BigDecimal.ZERO) > 0 && ask1.compareTo(BigDecimal.ZERO) > 0) {
                cache.updateBidAsk("binance", symbol, bid1, ask1);
                long exchangeTs = parseTimestamp(root.path("E"));
                if (exchangeTs > 0) {
                    long latencyMs = System.currentTimeMillis() - exchangeTs;
                    influxPublisher.publishPriceLatency("binance", symbol, latencyMs);
                }
            }
        } catch (Exception e) {
            log.warn("Binance bookTicker parse error: {}", e.getMessage());
        }
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        if (data != null && data.length > 0) {
            onMessage(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private BigDecimal parsePrice(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText());
        } catch (Exception e) {
            return null;
        }
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
