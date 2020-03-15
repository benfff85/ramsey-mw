package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.service.WorkUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static java.util.Objects.nonNull;

@Slf4j
@RestController
public class WorkUnitController {

    private final WorkUnitService workUnitService;

    public WorkUnitController(WorkUnitService workUnitService) {
        this.workUnitService = workUnitService;
    }

    @PostMapping("/api/ramsey/work-units")
    public ResponseEntity<List<WorkUnitDto>> saveWorkUnit(@RequestBody() List<WorkUnitDto> workUnitsToCreate) {
        log.info("Processing saveWorkUnit");
        List<WorkUnitDto> workUnits = workUnitService.saveAll(workUnitsToCreate);
        log.info("Work units posted, count: {}", workUnits.size());
        return ResponseEntity.ok().body(workUnits);
    }


    @GetMapping("/api/ramsey/work-units")
    public ResponseEntity<List<WorkUnitDto>> fetchWorkUnit(
            @RequestParam() WorkUnitStatus status,
            @RequestParam() Integer vertexCount,
            @RequestParam() Integer subgraphSize,
            @RequestParam(required = false) String assignedClientId,
            @RequestParam() Integer pageSize) {

        log.info("Processing fetchWorkUnit");

        List<WorkUnitDto> workUnits;
        if (nonNull(assignedClientId)) {
            workUnits = workUnitService.getAllAssignedToClient(status, vertexCount, subgraphSize, assignedClientId, pageSize);
        } else {
            workUnits = workUnitService.getAll(status, vertexCount, subgraphSize, pageSize);
        }

        log.info("Completed fetchWorkUnit");
        return ResponseEntity.ok().body(workUnits);

    }

    @GetMapping("/api/ramsey/work-units/last")
    public ResponseEntity<List<WorkUnitDto>> fetchLastWorkUnit(@RequestParam() Integer graphId) {
        log.info("Processing fetchLAstWorkUnit");
        List<WorkUnitDto> workUnit = workUnitService.getMostRecentForGraphId(graphId);
        log.info("Completed fetchLAstWorkUnit");
        return ResponseEntity.ok().body(workUnit);

    }

}