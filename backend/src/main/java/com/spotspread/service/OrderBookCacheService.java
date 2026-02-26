package com.spotspread.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单簿买一/卖一价格缓存，供套利任务读取。
 * BidAsk.updatedAt 为接收/更新时间戳(ms)，用于判断价格是否过期（如超过 500ms 弃用）。
 */
@Service
public class OrderBookCacheService {

    private final ConcurrentHashMap<String, BidAsk> cache = new ConcurrentHashMap<>();

    public record BidAsk(BigDecimal bid1, BigDecimal ask1, long updatedAt) {}

    public void updateBidAsk(String exchange, String symbol, BigDecimal bid1, BigDecimal ask1) {
        if (bid1 == null || ask1 == null || bid1.compareTo(BigDecimal.ZERO) <= 0 || ask1.compareTo(BigDecimal.ZERO) <= 0) return;
        String key = key(exchange, symbol);
        cache.put(key, new BidAsk(bid1, ask1, System.currentTimeMillis()));
    }

    public BidAsk getBidAsk(String exchange, String symbol) {
        return cache.get(key(exchange, symbol));
    }

    private static String key(String exchange, String symbol) {
        return (exchange + ":" + symbol).toLowerCase();
    }
}
