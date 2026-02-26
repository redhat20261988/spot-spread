package com.spotspread.repository;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SpreadArbitrageStatsRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<SpreadPairStatRow> PAIR_ROW_MAPPER = (rs, i) -> new SpreadPairStatRow(
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5),
            rs.getBigDecimal(6), rs.getBigDecimal(7)
    );

    public SpreadArbitrageStatsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveSnapshots(List<SnapshotRow> rows) {
        if (rows.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        String sql = "INSERT INTO spread_arbitrage_snapshots (symbol, exchange_buy, exchange_sell, spot_price_buy, spot_price_sell, spot_spread, profit_margin_pct, spot_fee_buy_pct, spot_fee_sell_pct, snapshot_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                SnapshotRow row = rows.get(i);
                ps.setString(1, row.symbol());
                ps.setString(2, row.exchangeBuy());
                ps.setString(3, row.exchangeSell());
                ps.setBigDecimal(4, row.spotPriceBuy());
                ps.setBigDecimal(5, row.spotPriceSell());
                ps.setBigDecimal(6, row.spotSpread());
                ps.setBigDecimal(7, row.profitMarginPct());
                ps.setBigDecimal(8, row.spotFeeBuyPct());
                ps.setBigDecimal(9, row.spotFeeSellPct());
                ps.setObject(10, now);
            }
            @Override
            public int getBatchSize() { return rows.size(); }
        });
    }

    /**
     * 全局聚合，按平均利润率降序、次数降序。
     */
    public List<SpreadPairStatRow> findAllPairStatsOrdered() {
        String sql = "SELECT symbol, exchange_buy, exchange_sell, COUNT(*) AS spread_count, AVG(profit_margin_pct) AS avg_profit_margin_pct, AVG(spot_fee_buy_pct) AS spot_fee_buy_pct, AVG(spot_fee_sell_pct) AS spot_fee_sell_pct FROM spread_arbitrage_snapshots GROUP BY symbol, exchange_buy, exchange_sell ORDER BY avg_profit_margin_pct DESC, spread_count DESC";
        return jdbcTemplate.query(sql, PAIR_ROW_MAPPER);
    }

    public record SnapshotRow(String symbol, String exchangeBuy, String exchangeSell, BigDecimal spotPriceBuy, BigDecimal spotPriceSell, BigDecimal spotSpread, BigDecimal profitMarginPct, BigDecimal spotFeeBuyPct, BigDecimal spotFeeSellPct) {}
    public record SpreadPairStatRow(String symbol, String exchangeBuy, String exchangeSell, int spreadCount, BigDecimal avgProfitMarginPct, BigDecimal spotFeeBuyPct, BigDecimal spotFeeSellPct) {}
}
