package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.entity.WorkResult;
import com.setminusx.ramsey.mw.service.WorkResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ramsey/results")
public class WorkResultController {

    private final WorkResultService workResultService;

    public WorkResultController(WorkResultService workResultService) {
        this.workResultService = workResultService;
    }

    @PostMapping
    public List<WorkResult> submitResults(@RequestBody List<WorkResult> results) {
        log.info("Submitting {} work results", results.size());
        return workResultService.saveResults(results);
    }

    @GetMapping
    public List<WorkResult> getResults(
            @RequestParam Integer stageId,
            @RequestParam(required = false) Integer maxCliqueCount) {
        if (maxCliqueCount != null) {
            return workResultService.getBestResults(stageId, maxCliqueCount);
        }
        return workResultService.getResultsByStageId(stageId);
    }

    @GetMapping("/count")
    public long getResultCount(@RequestParam Integer stageId) {
        return workResultService.getResultCountByStageId(stageId);
    }

}
