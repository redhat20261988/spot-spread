package com.spotspread.controller;

import com.spotspread.repository.SpreadArbitrageStatsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SpreadStatsController {

    private final SpreadArbitrageStatsRepository repository;

    public SpreadStatsController(SpreadArbitrageStatsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/spread-stats")
    public ResponseEntity<Map<String, Object>> getSpreadStats() {
        List<SpreadArbitrageStatsRepository.SpreadPairStatRow> pairStats = repository.findAllPairStatsOrdered();
        return ResponseEntity.ok(Map.of("pairStats", pairStats));
    }
}
