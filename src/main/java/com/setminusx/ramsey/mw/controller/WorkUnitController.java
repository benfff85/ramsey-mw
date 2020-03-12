package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.model.WorkUnitPriority;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.service.WorkUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@RestController
public class WorkUnitController {

    private final WorkUnitService workUnitService;

    public WorkUnitController(WorkUnitService workUnitService) {
        this.workUnitService = workUnitService;
    }

    @PostMapping("/api/ramsey/work-units")
    public ResponseEntity<List<WorkUnitDto>> postWorkUnit(@RequestBody(required = false) List<WorkUnitDto> workUnitsToCreate) {

        log.info("Processing postWorkUnit");
        List<WorkUnitDto> workUnits = null;

        if (nonNull(workUnitsToCreate)) {
            log.info("Posting work units");
            Date date = new Date();
            for (WorkUnitDto workUnitDto : workUnitsToCreate) {
                if (isNull(workUnitDto.getId())) {
                    log.debug("New work unit; setting default values");
                    workUnitDto.setCreatedDate(date);
                    workUnitDto.setAssignedDate(null);
                    workUnitDto.setCompletedDate(null);
                    workUnitDto.setProcessingStartedDate(null);
                    workUnitDto.setStatus(WorkUnitStatus.NEW);
                    workUnitDto.setCliqueCount(null);
                    workUnitDto.setAssignedClient(null);
                    workUnitDto.setPriority(WorkUnitPriority.MEDIUM);
                }
            }
            workUnits = workUnitService.saveAll(workUnitsToCreate);
            log.info("Work units posted, count: {}", workUnits.size());
        }

        log.info("Completed postWorkUnit");
        return ResponseEntity.ok().body(workUnits);

    }


    @GetMapping("/api/ramsey/work-units")
    public ResponseEntity<List<WorkUnitDto>> fetchWorkUnit(
            @RequestParam(required = false) WorkUnitStatus status,
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) String assignedClientId,
            @RequestParam(required = false) Integer pageSize) {

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

}