package com.spotspread.event;

/**
 * 价差与利润率事件：套利任务计算后发布，供 InfluxDB 监听器异步写入。
 */
public record SpreadProfitEvent(String exSell, String exBuy, String symbol,
                                double spotSpread, double profitMarginPct, double bid, double ask) {}
