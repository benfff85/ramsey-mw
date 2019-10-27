package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.model.WorkUnitPriority;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.service.WorkUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@RestController
public class WorkUnitController {

    @Autowired
    private WorkUnitService workUnitService;

    @PostMapping("/api/ramsey/work-units")
    public ResponseEntity postWorkUnit(
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Integer pageSize,
            @RequestBody(required = false) List<WorkUnitDto> workUnitsToCreate) {

        log.info("Processing postWorkUnit");

        List<WorkUnitDto> workUnits = null;

        if (workUnitsToCreate != null) {
            log.info("Posting work units");
            Date date = new Date();
            for (WorkUnitDto workUnitDto : workUnitsToCreate) {
                log.debug("New work unit; setting default values");
                if (workUnitDto.getId() == null) {
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
            workUnits = workUnitService.insertWorkUnits(workUnitsToCreate);
            log.info("Work units posted");
        } else {
            log.info("Assigning work units");
            workUnits = workUnitService.assignWorkUnit(vertexCount, subgraphSize, clientId, pageSize);
            log.info("Work units assigned");
        }

        log.info("Completed postWorkUnit");
        return ResponseEntity.ok().body(workUnits);

    }


    @GetMapping("/api/ramsey/work-units")
    public ResponseEntity fetchWorkUnit(
            @RequestParam(required = false) WorkUnitStatus status,
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) Integer pageSize) {

        log.info("Processing fetchWorkUnit");

        List<WorkUnitDto> workUnits = workUnitService.getAll(status, vertexCount, subgraphSize, pageSize);

        log.info("Completed fetchWorkUnit");
        return ResponseEntity.ok().body(workUnits);

    }

}