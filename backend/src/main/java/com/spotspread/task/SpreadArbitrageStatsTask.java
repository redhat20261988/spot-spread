package com.spotspread.task;

import com.spotspread.config.ExchangeFeeRates;
import com.spotspread.repository.SpreadArbitrageStatsRepository;
import com.spotspread.event.InfluxDbMessagePublisher;
import com.spotspread.service.OrderBookCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpreadArbitrageStatsTask {

    private static final Logger log = LoggerFactory.getLogger(SpreadArbitrageStatsTask.class);
    private static final List<String> SYMBOLS = List.of("BTC", "ETH", "SOL", "XRP", "HYPE", "BNB");
    private static final List<String> EXCHANGES = List.of(
            "binance", "bitfinex", "coinex", "okx", "bybit", "gateio", "bitget", "lbank", "whitebit",
            "bitunix", "cryptocom"
    );
    private static final BigDecimal THRESHOLD_PCT = new BigDecimal("0.5");
    /** 价格过期阈值(ms)，超过则弃用，避免过时价格导致跨所套利误判 */
    private static final long STALE_MS = 500;

    private final OrderBookCacheService cache;
    private final SpreadArbitrageStatsRepository repository;
    private final InfluxDbMessagePublisher influxPublisher;

    public SpreadArbitrageStatsTask(OrderBookCacheService cache, SpreadArbitrageStatsRepository repository,
                                    InfluxDbMessagePublisher influxPublisher) {
        this.cache = cache;
        this.repository = repository;
        this.influxPublisher = influxPublisher;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 15_000)
    public void run() {
        List<SpreadArbitrageStatsRepository.SnapshotRow> rows = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            try {
                collectSnapshots(symbol, rows);
            } catch (Exception e) {
                log.warn("[SpreadArbitrageStats] symbol={} error: {}", symbol, e.getMessage());
            }
        }
        if (!rows.isEmpty()) {
            repository.saveSnapshots(rows);
            log.debug("[SpreadArbitrageStats] saved {} rows", rows.size());
        }
    }

    private void collectSnapshots(String symbol, List<SpreadArbitrageStatsRepository.SnapshotRow> out) {
        String sym = symbol + "USDT";
        long now = System.currentTimeMillis();
        for (int i = 0; i < EXCHANGES.size(); i++) {
            for (int j = 0; j < EXCHANGES.size(); j++) {
                if (i == j) continue;
                String exSell = EXCHANGES.get(i);
                String exBuy = EXCHANGES.get(j);
                var sellBook = cache.getBidAsk(exSell, sym);
                var buyBook = cache.getBidAsk(exBuy, sym);
                if (sellBook == null || buyBook == null) continue;
                if (now - sellBook.updatedAt() > STALE_MS || now - buyBook.updatedAt() > STALE_MS) continue;
                BigDecimal aAsk = sellBook.ask1();
                BigDecimal bBid = buyBook.bid1();
                if (aAsk == null || bBid == null || bBid.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal rawPct = aAsk.divide(bBid, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
                BigDecimal makerSell = ExchangeFeeRates.getSpotMakerFeePct(exSell);
                BigDecimal takerSell = ExchangeFeeRates.getSpotTakerFeePct(exSell);
                BigDecimal makerBuy = ExchangeFeeRates.getSpotMakerFeePct(exBuy);
                BigDecimal takerBuy = ExchangeFeeRates.getSpotTakerFeePct(exBuy);
                if (makerSell == null || takerSell == null || makerBuy == null || takerBuy == null) continue;
                BigDecimal totalA = makerSell.add(takerBuy);
                BigDecimal totalB = takerSell.add(makerBuy);
                BigDecimal feeSell, feeBuy;
                if (totalA.compareTo(totalB) <= 0) {
                    feeSell = makerSell; feeBuy = takerBuy;
                } else {
                    feeSell = takerSell; feeBuy = makerBuy;
                }
                BigDecimal profitMarginPct = rawPct.subtract(feeSell).subtract(feeBuy);
                BigDecimal spotSpread = aAsk.subtract(bBid);
                influxPublisher.publishSpreadProfit(exSell, exBuy, symbol,
                        spotSpread.doubleValue(), profitMarginPct.doubleValue(),
                        bBid.doubleValue(), aAsk.doubleValue());
                if (profitMarginPct.compareTo(THRESHOLD_PCT) < 0) continue;
                out.add(new SpreadArbitrageStatsRepository.SnapshotRow(
                        symbol, exBuy, exSell, bBid, aAsk, spotSpread, profitMarginPct, feeBuy, feeSell
                ));
            }
        }
    }
}
