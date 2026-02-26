package com.spotspread.event;

import com.spotspread.service.InfluxMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * InfluxDB 消息监听器。
 * 异步接收 WebSocket/套利任务发布的事件，并写入 InfluxDB。
 */
@Component
public class InfluxDbMessageListener {

    private static final Logger log = LoggerFactory.getLogger(InfluxDbMessageListener.class);

    private final InfluxMetricsService influx;

    public InfluxDbMessageListener(InfluxMetricsService influx) {
        this.influx = influx;
    }

    @Async
    @EventListener
    public void onPriceLatency(PriceLatencyEvent event) {
        try {
            influx.writePriceLatency(event.exchange(), event.symbol(), event.latencyMs());
        } catch (Exception e) {
            log.debug("[InfluxDB] price latency listener error: {}", e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onSpreadProfit(SpreadProfitEvent event) {
        try {
            influx.writeSpreadProfit(
                    event.exSell(), event.exBuy(), event.symbol(),
                    event.spotSpread(), event.profitMarginPct(),
                    event.bid(), event.ask()
            );
        } catch (Exception e) {
            log.debug("[InfluxDB] spread profit listener error: {}", e.getMessage());
        }
    }
}
