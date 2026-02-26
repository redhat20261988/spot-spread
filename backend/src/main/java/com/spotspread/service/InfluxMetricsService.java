package com.spotspread.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;

/**
 * InfluxDB 时序指标写入服务。
 * 异步写入：价格延迟（price_latency）、价差与利润率（spread_profit）。
 */
@Service
public class InfluxMetricsService {

    private static final Logger log = LoggerFactory.getLogger(InfluxMetricsService.class);

    @Value("${influxdb.url:http://localhost:8086}")
    private String url;

    @Value("${influxdb.token:}")
    private String token;

    @Value("${influxdb.org:spot-spread}")
    private String org;

    @Value("${influxdb.bucket:spot-spread}")
    private String bucket;

    @Value("${influxdb.enabled:true}")
    private boolean enabled;

    private InfluxDBClient client;
    private WriteApi writeApi;

    @PostConstruct
    public void init() {
        if (!enabled || token == null || token.isBlank()) {
            log.info("[InfluxDB] disabled or token not configured, metrics will not be written");
            return;
        }
        try {
            client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
            writeApi = client.makeWriteApi();
            log.info("[InfluxDB] connected to {} org={} bucket={}", url, org, bucket);
        } catch (Exception e) {
            log.warn("[InfluxDB] failed to connect: {}", e.getMessage());
            enabled = false;
        }
    }

    @PreDestroy
    public void destroy() {
        if (writeApi != null) {
            try {
                writeApi.close();
            } catch (Exception e) {
                log.debug("[InfluxDB] writeApi close: {}", e.getMessage());
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("[InfluxDB] client close: {}", e.getMessage());
            }
        }
    }

    private boolean isWritable() {
        return enabled && writeApi != null;
    }

    /**
     * 写入价格延迟（交易所时间戳与本地时间的差值）。
     */
    public void writePriceLatency(String exchange, String symbol, long latencyMs) {
        if (!isWritable()) return;
        try {
            Point point = Point.measurement("price_latency")
                    .addTag("exchange", exchange)
                    .addTag("symbol", symbol)
                    .addField("latency_ms", latencyMs)
                    .time(Instant.now(), WritePrecision.MS);
            writeApi.writePoint(point);
        } catch (Exception e) {
            log.debug("[InfluxDB] writePriceLatency error: {}", e.getMessage());
        }
    }

    /**
     * 写入价差与利润率。
     */
    public void writeSpreadProfit(String exSell, String exBuy, String symbol,
                                   double spotSpread, double profitMarginPct, double bid, double ask) {
        if (!isWritable()) return;
        try {
            Point point = Point.measurement("spread_profit")
                    .addTag("ex_sell", exSell)
                    .addTag("ex_buy", exBuy)
                    .addTag("symbol", symbol)
                    .addField("spot_spread", spotSpread)
                    .addField("profit_margin_pct", profitMarginPct)
                    .addField("bid", bid)
                    .addField("ask", ask)
                    .time(Instant.now(), WritePrecision.MS);
            writeApi.writePoint(point);
        } catch (Exception e) {
            log.debug("[InfluxDB] writeSpreadProfit error: {}", e.getMessage());
        }
    }
}
