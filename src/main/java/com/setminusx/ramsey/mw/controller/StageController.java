package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.service.StageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/ramsey/stages")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    @GetMapping
    public List<Stage> getStages(
            @RequestParam(required = false) Integer campaignId,
            @RequestParam(required = false) Stage.Status status) {

        log.info("Fetching stages with filters - CampaignId: {}, Status: {}", campaignId, status);
        return stageService.getStages(campaignId, status);
    }

    @GetMapping("/{id}")
    public Stage getStageById(@PathVariable Integer id) {
        log.info("Fetching stage with ID: {}", id);

        Stage stage = stageService.getStageById(id);

        if (stage == null) {
            log.warn("Stage with ID: {} not found", id);
            throw new ResponseStatusException(NOT_FOUND, "Stage not found");
        }

        return stage;
    }

    @PostMapping
    public Stage createStage(@RequestBody Stage stage) {
        log.info("Creating a new stage with data: {}", stage);
        return stageService.createOrUpdateStage(stage);
    }

    @PutMapping("/{id}")
    public Stage updateStage(@PathVariable Integer id, @RequestBody Stage stage) {
        log.info("Updating stage with ID: {} with data: {}", id, stage);
        stage.setStageId(id);
        return stageService.createOrUpdateStage(stage);
    }

    @DeleteMapping("/{id}")
    public void deleteStage(@PathVariable Integer id) {
        log.info("Deleting stage with ID: {}", id);
        stageService.deleteStage(id);
    }

}