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

import static com.setminusx.ramsey.mw.utility.Constants.ERROR_HEADER;

@Slf4j
@RestController
public class WorkUnitController {

    @Autowired
    private WorkUnitService workUnitService;

    @PostMapping("/api/ramsey/work-units")
    public ResponseEntity registerWorkUnit(
            @RequestBody() WorkUnitDto workUnitDto) {

        log.info("Processing registerWorkUnit");
        Date date = new Date();
        workUnitDto.setCreatedDate(date);
        workUnitDto.setAssignedDate(null);
        workUnitDto.setCompletedDate(null);
        workUnitDto.setProcessingStartedDate(null);
        workUnitDto.setStatus(WorkUnitStatus.NEW);
        workUnitDto.setCliqueCount(null);
        workUnitDto.setAssignedClient(null);
        workUnitDto.setPriority(WorkUnitPriority.MEDIUM);
        log.info(workUnitDto.toString());
        workUnitService.insertWorkUnit(workUnitDto);
        log.info("Completed registerWorkUnit");

        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/ramsey/work-units")
    public ResponseEntity fetchWorkUnit(
            @RequestParam(required = false) WorkUnitStatus status,
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) boolean triggerAssignment,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Integer pageSize) {

        log.info("Processing fetchWorkUnit");

        List<WorkUnitDto> workUnits;

        if (triggerAssignment) {
            if (clientId == null) {
                log.error("Assignment of work units requested but no client id provided");
                return ResponseEntity.badRequest().header(ERROR_HEADER, "Assignment of work units requested but no client id provided").build();
            }
            log.info("Assigning queried work units to client {}", clientId);
            workUnits = workUnitService.getAllWithAssignment(status, vertexCount, subgraphSize, clientId, pageSize);
        }  else {
            workUnits = workUnitService.getAll(status, vertexCount, subgraphSize, pageSize);
        }
        log.info("Completed fetchWorkUnit");
        return ResponseEntity.ok().body(workUnits);

    }

}