package com.spotspread.task;

import com.spotspread.config.ArbitrageConfig;
import com.spotspread.config.ArbitrageConfig.ProfitMode;
import com.spotspread.config.ExchangeFeeRates;
import com.spotspread.repository.SpreadArbitrageStatsRepository.SnapshotRow;
import com.spotspread.event.InfluxDbMessagePublisher;
import com.spotspread.event.SpreadSnapshotSaveEvent;
import com.spotspread.service.OrderBookCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpreadArbitrageStatsTask {

    private static final Logger log = LoggerFactory.getLogger(SpreadArbitrageStatsTask.class);
    private volatile boolean initialized = false;
    private static final List<String> SYMBOLS = List.of("BTC", "ETH", "SOL", "XRP", "HYPE", "BNB");
    private static final List<String> EXCHANGES = List.of(
            "binance", "bitfinex", "coinex", "okx", "bybit", "gateio", "bitget", "lbank", "whitebit",
            "bitunix", "cryptocom"
    );
    private static final BigDecimal THRESHOLD_PCT = new BigDecimal("0.5");
    private static final long STALE_MS = 500;

    private final OrderBookCacheService cache;
    private final InfluxDbMessagePublisher influxPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final ArbitrageConfig arbitrageConfig;

    public SpreadArbitrageStatsTask(OrderBookCacheService cache,
                                    InfluxDbMessagePublisher influxPublisher,
                                    ApplicationEventPublisher eventPublisher,
                                    ArbitrageConfig arbitrageConfig) {
        this.cache = cache;
        this.influxPublisher = influxPublisher;
        this.eventPublisher = eventPublisher;
        this.arbitrageConfig = arbitrageConfig;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 15_000)
    public void run() {
        if (!initialized) {
            log.info("[SpreadArbitrageStats] 启动利润率计算任务，模式: {}", arbitrageConfig.getProfitMode());
            initialized = true;
        }
        List<SnapshotRow> rows = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            try {
                collectSnapshots(symbol, rows);
            } catch (Exception e) {
                log.warn("[SpreadArbitrageStats] symbol={} error: {}", symbol, e.getMessage());
            }
        }
        if (!rows.isEmpty()) {
            eventPublisher.publishEvent(new SpreadSnapshotSaveEvent(rows));
            log.debug("[SpreadArbitrageStats] 发布保存事件，共 {} 条记录", rows.size());
        }
    }

    private void collectSnapshots(String symbol, List<SnapshotRow> out) {
        String sym = symbol + "USDT";
        long now = System.currentTimeMillis();
        ProfitMode mode = arbitrageConfig.getProfitMode();

        for (int i = 0; i < EXCHANGES.size(); i++) {
            for (int j = i + 1; j < EXCHANGES.size(); j++) {
                String exA = EXCHANGES.get(i);
                String exB = EXCHANGES.get(j);
                var bookA = cache.getBidAsk(exA, sym);
                var bookB = cache.getBidAsk(exB, sym);
                if (bookA == null || bookB == null) continue;
                if (now - bookA.updatedAt() > STALE_MS || now - bookB.updatedAt() > STALE_MS) continue;

                BigDecimal aBid = bookA.bid1(), aAsk = bookA.ask1();
                BigDecimal bBid = bookB.bid1(), bAsk = bookB.ask1();
                if (aBid == null || aAsk == null || bBid == null || bAsk == null) continue;
                if (aBid.compareTo(BigDecimal.ZERO) <= 0 || bBid.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (aAsk.compareTo(BigDecimal.ZERO) <= 0 || bAsk.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal makerA = ExchangeFeeRates.getSpotMakerFeePct(exA);
                BigDecimal takerA = ExchangeFeeRates.getSpotTakerFeePct(exA);
                BigDecimal makerB = ExchangeFeeRates.getSpotMakerFeePct(exB);
                BigDecimal takerB = ExchangeFeeRates.getSpotTakerFeePct(exB);
                if (makerA == null || takerA == null || makerB == null || takerB == null) continue;

                switch (mode) {
                    case MAX_PROFIT -> collectMaxProfit(symbol, exA, exB, aBid, aAsk, bBid, bAsk, makerA, takerA, makerB, takerB, out);
                    case TAKER_MAKER -> collectTakerMaker(symbol, exA, exB, aBid, aAsk, bBid, bAsk, makerA, takerA, makerB, takerB, out);
                    case TAKER_TAKER -> collectTakerTaker(symbol, exA, exB, aBid, aAsk, bBid, bAsk, takerA, takerB, out);
                }
            }
        }
    }

    /**
     * 最大利润率模式：选择 maker+taker 组合中总手续费最小的。
     */
    private void collectMaxProfit(String symbol, String exA, String exB,
                                  BigDecimal aBid, BigDecimal aAsk, BigDecimal bBid, BigDecimal bAsk,
                                  BigDecimal makerA, BigDecimal takerA, BigDecimal makerB, BigDecimal takerB,
                                  List<SnapshotRow> out) {
        // 方向1：在 B 买入(bid)，在 A 卖出(ask)
        calcMaxProfitDirection(symbol, exA, exB, aAsk, bBid, makerA, takerA, makerB, takerB, out);
        // 方向2：在 A 买入(bid)，在 B 卖出(ask)
        calcMaxProfitDirection(symbol, exB, exA, bAsk, aBid, makerB, takerB, makerA, takerA, out);
    }

    private void calcMaxProfitDirection(String symbol, String exSell, String exBuy,
                                        BigDecimal sellAsk, BigDecimal buyBid,
                                        BigDecimal makerSell, BigDecimal takerSell,
                                        BigDecimal makerBuy, BigDecimal takerBuy,
                                        List<SnapshotRow> out) {
        BigDecimal rawPct = sellAsk.divide(buyBid, 6, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
        BigDecimal totalA = makerSell.add(takerBuy);
        BigDecimal totalB = takerSell.add(makerBuy);
        BigDecimal feeSell, feeBuy;
        if (totalA.compareTo(totalB) <= 0) {
            feeSell = makerSell; feeBuy = takerBuy;
        } else {
            feeSell = takerSell; feeBuy = makerBuy;
        }
        BigDecimal profitPct = rawPct.subtract(feeSell).subtract(feeBuy);
        BigDecimal spread = sellAsk.subtract(buyBid);

        influxPublisher.publishSpreadProfit(exSell, exBuy, symbol,
                spread.doubleValue(), profitPct.doubleValue(),
                buyBid.doubleValue(), sellAsk.doubleValue());

        if (profitPct.compareTo(THRESHOLD_PCT) >= 0) {
            out.add(new SnapshotRow(symbol, exBuy, exSell, buyBid, sellAsk, spread, profitPct, feeBuy, feeSell));
        }
    }

    /**
     * taker+maker 模式：先高卖后低买 / 先低买后高卖。
     */
    private void collectTakerMaker(String symbol, String exA, String exB,
                                   BigDecimal aBid, BigDecimal aAsk, BigDecimal bBid, BigDecimal bAsk,
                                   BigDecimal makerA, BigDecimal takerA, BigDecimal makerB, BigDecimal takerB,
                                   List<SnapshotRow> out) {
        // 方向1：先高卖后低买（基于卖1价）
        BigDecimal profitAsk, spreadAsk, feeBuyAsk, feeSellAsk;
        String exBuyAsk, exSellAsk;
        BigDecimal priceBuyAsk, priceSellAsk;
        if (aAsk.compareTo(bAsk) > 0) {
            // A.ask1 > B.ask1：先在 A 以 ask1 卖出，后在 B 以 ask1 买入
            profitAsk = aAsk.divide(bAsk, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                    .subtract(makerA).subtract(takerB);
            spreadAsk = aAsk.subtract(bAsk);
            exSellAsk = exA; exBuyAsk = exB;
            priceSellAsk = aAsk; priceBuyAsk = bAsk;
            feeSellAsk = makerA; feeBuyAsk = takerB;
        } else {
            // B.ask1 > A.ask1
            profitAsk = bAsk.divide(aAsk, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                    .subtract(makerB).subtract(takerA);
            spreadAsk = bAsk.subtract(aAsk);
            exSellAsk = exB; exBuyAsk = exA;
            priceSellAsk = bAsk; priceBuyAsk = aAsk;
            feeSellAsk = makerB; feeBuyAsk = takerA;
        }
        influxPublisher.publishSpreadProfit(exSellAsk, exBuyAsk, symbol,
                spreadAsk.doubleValue(), profitAsk.doubleValue(),
                priceBuyAsk.doubleValue(), priceSellAsk.doubleValue());
        if (profitAsk.compareTo(THRESHOLD_PCT) >= 0) {
            out.add(new SnapshotRow(symbol, exBuyAsk, exSellAsk, priceBuyAsk, priceSellAsk, spreadAsk, profitAsk, feeBuyAsk, feeSellAsk));
        }

        // 方向2：先低买后高卖（基于买1价）
        BigDecimal profitBid, spreadBid, feeBuyBid, feeSellBid;
        String exBuyBid, exSellBid;
        BigDecimal priceBuyBid, priceSellBid;
        if (aBid.compareTo(bBid) > 0) {
            // A.bid1 > B.bid1：先在 B 以 bid1 买入，后在 A 以 bid1 卖出
            profitBid = aBid.divide(bBid, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                    .subtract(takerA).subtract(makerB);
            spreadBid = aBid.subtract(bBid);
            exSellBid = exA; exBuyBid = exB;
            priceSellBid = aBid; priceBuyBid = bBid;
            feeSellBid = takerA; feeBuyBid = makerB;
        } else {
            // B.bid1 > A.bid1
            profitBid = bBid.divide(aBid, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                    .subtract(takerB).subtract(makerA);
            spreadBid = bBid.subtract(aBid);
            exSellBid = exB; exBuyBid = exA;
            priceSellBid = bBid; priceBuyBid = aBid;
            feeSellBid = takerB; feeBuyBid = makerA;
        }
        influxPublisher.publishSpreadProfit(exSellBid, exBuyBid, symbol,
                spreadBid.doubleValue(), profitBid.doubleValue(),
                priceBuyBid.doubleValue(), priceSellBid.doubleValue());
        if (profitBid.compareTo(THRESHOLD_PCT) >= 0) {
            out.add(new SnapshotRow(symbol, exBuyBid, exSellBid, priceBuyBid, priceSellBid, spreadBid, profitBid, feeBuyBid, feeSellBid));
        }
    }

    /**
     * taker+taker 模式：在 B 所以 ask1 买入，在 A 所以 bid1 卖出。
     */
    private void collectTakerTaker(String symbol, String exA, String exB,
                                   BigDecimal aBid, BigDecimal aAsk, BigDecimal bBid, BigDecimal bAsk,
                                   BigDecimal takerA, BigDecimal takerB,
                                   List<SnapshotRow> out) {
        // 方向1：在 B 以 ask1 买入，在 A 以 bid1 卖出
        BigDecimal profit1 = aBid.divide(bAsk, 6, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                .subtract(takerA).subtract(takerB);
        BigDecimal spread1 = aBid.subtract(bAsk);
        influxPublisher.publishSpreadProfit(exA, exB, symbol,
                spread1.doubleValue(), profit1.doubleValue(),
                bAsk.doubleValue(), aBid.doubleValue());
        if (profit1.compareTo(THRESHOLD_PCT) >= 0) {
            out.add(new SnapshotRow(symbol, exB, exA, bAsk, aBid, spread1, profit1, takerB, takerA));
        }

        // 方向2：在 A 以 ask1 买入，在 B 以 bid1 卖出
        BigDecimal profit2 = bBid.divide(aAsk, 6, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                .subtract(takerB).subtract(takerA);
        BigDecimal spread2 = bBid.subtract(aAsk);
        influxPublisher.publishSpreadProfit(exB, exA, symbol,
                spread2.doubleValue(), profit2.doubleValue(),
                aAsk.doubleValue(), bBid.doubleValue());
        if (profit2.compareTo(THRESHOLD_PCT) >= 0) {
            out.add(new SnapshotRow(symbol, exA, exB, aAsk, bBid, spread2, profit2, takerA, takerB));
        }
    }
}
