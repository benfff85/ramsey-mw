package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.repository.StageRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class StageService {

    private final StageRepo stageRepo;

    public List<Stage> getStages(Integer campaignId, Stage.Status status) {
        if (campaignId == null && status == null) {
            return stageRepo.findAll();
        } else {
            return stageRepo.findByCampaignIdAndStatus(campaignId, status);
        }
    }

    public Stage getStageById(Integer id) {
        return stageRepo.findById(id).orElse(null);
    }

    public Stage createOrUpdateStage(Stage stage) {
        return stageRepo.save(stage);
    }

    public void deleteStage(Integer id) {
        stageRepo.deleteById(id);
    }

}
