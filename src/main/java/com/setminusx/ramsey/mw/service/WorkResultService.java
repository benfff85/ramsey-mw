package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.WorkResult;
import com.setminusx.ramsey.mw.repository.WorkResultRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkResultService {

    private final WorkResultRepo workResultRepo;

    public WorkResultService(WorkResultRepo workResultRepo) {
        this.workResultRepo = workResultRepo;
    }

    public List<WorkResult> saveResults(List<WorkResult> results) {
        return workResultRepo.saveAll(results);
    }

    public WorkResult saveResult(WorkResult result) {
        return workResultRepo.save(result);
    }

    public List<WorkResult> getResultsByStageId(Integer stageId) {
        return workResultRepo.findByStageId(stageId);
    }

    public long getResultCountByStageId(Integer stageId) {
        return workResultRepo.countByStageId(stageId);
    }

    public List<WorkResult> getBestResults(Integer stageId, Integer maxCliqueCount) {
        return workResultRepo.findBestResultsByStageId(stageId, maxCliqueCount);
    }

}
