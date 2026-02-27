package com.spotspread.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 套利配置，支持三种利润率计算模式。
 */
@Configuration
@ConfigurationProperties(prefix = "arbitrage")
public class ArbitrageConfig {

    /**
     * 利润率计算模式枚举。
     */
    public enum ProfitMode {
        /** 最大利润率：选择 maker+taker 组合中总手续费最小的 */
        MAX_PROFIT,
        /** taker+maker 模式：先高卖后低买 / 先低买后高卖 */
        TAKER_MAKER,
        /** taker+taker 模式：在 B 所买入后在 A 所卖出 */
        TAKER_TAKER
    }

    private ProfitMode profitMode = ProfitMode.TAKER_TAKER;

    public ProfitMode getProfitMode() {
        return profitMode;
    }

    public void setProfitMode(ProfitMode profitMode) {
        this.profitMode = profitMode;
    }
}
