package com.spotspread.event;

import com.spotspread.repository.SpreadArbitrageStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步监听价差快照保存事件，执行 MySQL batchUpdate。
 */
@Component
public class SpreadSnapshotSaveListener {

    private static final Logger log = LoggerFactory.getLogger(SpreadSnapshotSaveListener.class);

    private final SpreadArbitrageStatsRepository repository;

    public SpreadSnapshotSaveListener(SpreadArbitrageStatsRepository repository) {
        this.repository = repository;
    }

    @Async
    @EventListener
    public void onSaveEvent(SpreadSnapshotSaveEvent event) {
        if (event.rows() == null || event.rows().isEmpty()) return;
        try {
            repository.saveSnapshots(event.rows());
            log.debug("[SpreadSnapshotSave] 异步保存 {} 条记录", event.rows().size());
        } catch (Exception e) {
            log.warn("[SpreadSnapshotSave] 保存失败: {}", e.getMessage());
        }
    }
}
