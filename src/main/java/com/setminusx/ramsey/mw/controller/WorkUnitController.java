package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.entity.WorkUnit;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.service.WorkUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/ramsey/work-units")
public class WorkUnitController {

    private final WorkUnitService workUnitService;

    public WorkUnitController(WorkUnitService workUnitService) {
        this.workUnitService = workUnitService;
    }

    @GetMapping
    public List<WorkUnit> fetchWorkUnit(
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) WorkUnitStatus status,
            @RequestParam(required = false) String assignedClientId,
            @RequestParam(required = false) Integer pageSize) {

        log.info("Fetching work units with filters - SubgraphSize: {}, VertexCount: {}, Status: {}, AssignedClientId: {}", subgraphSize, vertexCount, status, assignedClientId);
        return workUnitService.getWorkUnits(status, vertexCount, subgraphSize, assignedClientId, pageSize);
    }

    @GetMapping("/{id}")
    public WorkUnit getWorkUnitById(@PathVariable Integer id) {
        log.info("Fetching work unit with ID: {}", id);

        WorkUnit workUnit = workUnitService.getWorkUnitById(id);

        if (workUnit == null) {
            log.warn("Work unit with ID: {} not found", id);
            throw new ResponseStatusException(NOT_FOUND, "Work unit not found");
        }

        return workUnit;
    }

    @PostMapping
    public List<WorkUnit> createWorkUnits(@RequestBody() List<WorkUnit> workUnits) {
        log.info("Creating {} new work units", workUnits.size());
        List<WorkUnit> createdWorkUnits = workUnitService.createOrUpdateWorkUnits(workUnits);
        log.info("Work units created, count: {}", createdWorkUnits.size());
        return createdWorkUnits;
    }

    @PutMapping
    public List<WorkUnit> UpdateWorkUnits(@RequestBody() List<WorkUnit> workUnits) {
        log.info("Updating {} work units, first work unit id {}", workUnits.size(), workUnits.getFirst().getId());
        List<WorkUnit> updatedWorkUnits = workUnitService.createOrUpdateWorkUnits(workUnits);
        log.info("Work units updated, count: {}, first work unit id: {}", updatedWorkUnits.size(), updatedWorkUnits.getFirst().getId());
        return updatedWorkUnits;
    }

    @DeleteMapping("/{id}")
    public void deleteWorkUnit(@PathVariable Integer id) {
        log.info("Deleting work unit with ID: {}", id);
        workUnitService.deleteWorkUnit(id);
    }

    // TODO Remove once campaign is implemented
    @GetMapping("/last")
    public List<WorkUnit> fetchLastWorkUnit(@RequestParam() Integer graphId) {
        log.info("Processing fetchLastWorkUnit");
        List<WorkUnit> workUnit = workUnitService.getMostRecentForGraphId(graphId);
        log.info("Completed fetchLastWorkUnit");
        return workUnit;

    }

}