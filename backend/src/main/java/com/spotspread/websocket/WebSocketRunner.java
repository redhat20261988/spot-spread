package com.spotspread.websocket;

import com.spotspread.service.OrderBookCacheService;
import com.spotspread.websocket.handler.BinanceSpotDepthHandler;
import com.spotspread.websocket.handler.BitfinexSpotDepthHandler;
import com.spotspread.websocket.handler.CoinExSpotDepthHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebSocketRunner {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRunner.class);
    private final OrderBookCacheService cache;
    private final List<ManagedWebSocket> clients = new ArrayList<>();

    public WebSocketRunner(OrderBookCacheService cache) {
        this.cache = cache;
    }

    @PostConstruct
    public void start() {
        try {
            clients.add(new BinanceSpotDepthHandler(cache).createClient());
            clients.add(new BitfinexSpotDepthHandler(cache).createClient());
            clients.add(new CoinExSpotDepthHandler(cache).createClient());
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
