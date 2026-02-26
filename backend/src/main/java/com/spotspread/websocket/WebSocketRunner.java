package com.spotspread.websocket;

import com.spotspread.event.InfluxDbMessagePublisher;
import com.spotspread.service.OrderBookCacheService;
import com.spotspread.websocket.handler.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动所有支持现货深度 WebSocket 的交易所连接。
 * 参考 experiment 项目交易所列表，不支持 spot order book WebSocket 的交易所不接入。
 */
@Component
public class WebSocketRunner {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRunner.class);
    private final OrderBookCacheService cache;
    private final InfluxDbMessagePublisher influxPublisher;
    private final List<ManagedWebSocket> clients = new ArrayList<>();

    public WebSocketRunner(OrderBookCacheService cache, InfluxDbMessagePublisher influxPublisher) {
        this.cache = cache;
        this.influxPublisher = influxPublisher;
    }

    @PostConstruct
    public void start() {
        try {
            clients.add(new BinanceSpotDepthHandler(cache, influxPublisher).createClient());
            clients.add(new BitfinexSpotDepthHandler(cache).createClient());
            clients.add(new CoinExSpotDepthHandler(cache).createClient());
            clients.add(new OkxSpotDepthHandler(cache, influxPublisher).createClient());
            clients.add(new BybitSpotDepthHandler(cache, influxPublisher).createClient());
            clients.add(new GateSpotDepthHandler(cache).createClient());
            clients.add(new BitgetSpotDepthHandler(cache).createClient());
            clients.add(new LBankSpotDepthHandler(cache).createClient());
            clients.add(new WhiteBitSpotDepthHandler(cache).createClient());
            clients.add(new BitunixSpotDepthHandler(cache).createClient());
            clients.add(new CryptoComSpotDepthHandler(cache).createClient());
            for (ManagedWebSocket client : clients) client.connect();
            log.info("Started {} spot depth WebSocket connections", clients.size());
        } catch (Exception e) {
            log.error("Failed to start WebSocket clients", e);
        }
    }

    @PreDestroy
    public void stop() {
        for (ManagedWebSocket client : clients) {
            try { client.disconnect(); } catch (Exception e) { log.warn("Disconnect error: {}", e.getMessage()); }
        }
    }
}
