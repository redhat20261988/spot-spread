package com.spotspread.event;

/**
 * 价格延迟事件：WebSocket 收到交易所价格后发布，供 InfluxDB 监听器异步写入。
 */
public record PriceLatencyEvent(String exchange, String symbol, long latencyMs) {}
