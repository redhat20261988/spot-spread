CREATE TABLE IF NOT EXISTS spread_arbitrage_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    exchange_buy VARCHAR(32) NOT NULL,
    exchange_sell VARCHAR(32) NOT NULL,
    spot_price_buy DECIMAL(20,8) NOT NULL,
    spot_price_sell DECIMAL(20,8) NOT NULL,
    spot_spread DECIMAL(20,8) NOT NULL,
    profit_margin_pct DECIMAL(10,4) NOT NULL,
    spot_fee_buy_pct DECIMAL(10,4) NULL,
    spot_fee_sell_pct DECIMAL(10,4) NULL,
    snapshot_time DATETIME(3) NOT NULL,
    INDEX idx_symbol_time (symbol, snapshot_time),
    INDEX idx_symbol_pair (symbol, exchange_buy, exchange_sell)
);
