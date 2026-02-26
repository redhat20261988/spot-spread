package com.spotspread.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * InfluxDB 写入消息发布器。
 * WebSocket Handler 和 SpreadArbitrageStatsTask 发布事件，InfluxDbMessageListener 异步监听并写入 InfluxDB。
 */
@Component
public class InfluxDbMessagePublisher {

    private final ApplicationEventPublisher publisher;

    public InfluxDbMessagePublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishPriceLatency(String exchange, String symbol, long latencyMs) {
        publisher.publishEvent(new PriceLatencyEvent(exchange, symbol, latencyMs));
    }

    public void publishSpreadProfit(String exSell, String exBuy, String symbol,
                                    double spotSpread, double profitMarginPct, double bid, double ask) {
        publisher.publishEvent(new SpreadProfitEvent(exSell, exBuy, symbol, spotSpread, profitMarginPct, bid, ask));
    }
}
