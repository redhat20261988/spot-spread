package com.spotspread.event;

import com.spotspread.repository.SpreadArbitrageStatsRepository.SnapshotRow;

import java.util.List;

/**
 * 价差快照保存事件，携带待保存的行列表。
 */
public record SpreadSnapshotSaveEvent(List<SnapshotRow> rows) {}
