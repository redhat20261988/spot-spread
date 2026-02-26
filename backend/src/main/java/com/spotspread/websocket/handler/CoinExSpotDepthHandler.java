package com.spotspread.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotspread.service.OrderBookCacheService;
import com.spotspread.websocket.ExchangeWebSocketHandler;
import com.spotspread.websocket.ManagedWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * CoinEx 现货 depth.subscribe，提取买一/卖一。
 * wss://socket.coinex.com/v2/spot
 * 服务端返回 gzip 压缩，需先解压再解析。
 */
public class CoinExSpotDepthHandler implements ExchangeWebSocketHandler {

    private static final String WS_URL = "wss://socket.coinex.com/v2/spot";
    private static final Logger log = LoggerFactory.getLogger(CoinExSpotDepthHandler.class);

    private final OrderBookCacheService cache;
    private final ObjectMapper om = new ObjectMapper();

    private static final String[][] MARKET_LIST = {
            {"BTCUSDT", "10", "0", "false"},
            {"ETHUSDT", "10", "0", "false"},
            {"SOLUSDT", "10", "0", "false"},
            {"XRPUSDT", "10", "0", "false"},
            {"HYPEUSDT", "10", "0", "false"},
            {"BNBUSDT", "10", "0", "false"}
    };

    private static final String PING_MSG = "{\"method\":\"server.ping\",\"params\":{},\"id\":1}";

    public CoinExSpotDepthHandler(OrderBookCacheService cache) {
        this.cache = cache;
    }

    public ManagedWebSocket createClient() {
        return new ManagedWebSocket("coinex", URI.create(WS_URL), this);
    }

    @Override
    public void onConnected(ManagedWebSocket client) {
        log.info("CoinEx spot depth WebSocket connected");
        // v2 API: 使用 market_list 格式，单次订阅多市场
        List<String> list = new ArrayList<>();
        for (String[] m : MARKET_LIST) {
            list.add("[\"" + m[0] + "\"," + m[1] + ",\"" + m[2] + "\"," + m[3] + "]");
        }
        String marketListJson = "[" + String.join(",", list) + "]";
        String sub = "{\"method\":\"depth.subscribe\",\"params\":{\"market_list\":" + marketListJson + "},\"id\":1}";
        client.send(sub);
    }

    @Override
    public String getHeartbeatMessage() {
        return PING_MSG;
    }

    @Override
    public long getHeartbeatIntervalMs() {
        return 25_000;
    }

    @Override
    public void onBinaryMessage(byte[] data) {
        if (data == null || data.length == 0) return;
        try {
            String json = decompressGzip(data);
            processMessage(json);
        } catch (Exception e) {
            log.warn("CoinEx 解压/解析失败: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(String message) {
        processMessage(message);
    }

    private void processMessage(String message) {
        try {
            JsonNode root = om.readTree(message);
            String method = root.path("method").asText("");
            if (!"depth.update".equals(method)) return;
            // v2 格式: data.market, data.depth.bids, data.depth.asks
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return;
            String symbol = data.path("market").asText("");
            if (symbol.isEmpty()) return;
            JsonNode depth = data.path("depth");
            BigDecimal bid1 = parseBest(depth.path("bids"), 0);
            BigDecimal ask1 = parseBest(depth.path("asks"), 0);
            if (bid1 != null && ask1 != null) {
                cache.updateBidAsk("coinex", symbol, bid1, ask1);
            }
        } catch (Exception e) {
            log.warn("CoinEx depth.update 解析失败: {}", e.getMessage());
        }
    }

    private String decompressGzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
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
