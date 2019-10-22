package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.WorkUnitDto;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.service.WorkUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@Slf4j
@RestController
public class WorkUnitController {

    @Autowired
    private WorkUnitService workUnitService;

    @PostMapping("/api/ramsey/work-units")
    public ResponseEntity registerWorkUnit(
            @RequestHeader() String rqid,
            @RequestBody() WorkUnitDto workUnitDto) {

        log.info("Processing registerWorkUnit for rqid: {}", rqid);

        Date date = new Date();
        workUnitDto.setCreatedDate(date);
        workUnitDto.setAssignedDate(null);
        workUnitDto.setCompletedDate(null);
        workUnitDto.setStatus(WorkUnitStatus.NEW);
        workUnitDto.setCliqueCount(null);
        workUnitDto.setAssignedClient(null);
        log.info(workUnitDto.toString());
        workUnitService.insertWorkUnit(workUnitDto);
        return ResponseEntity.ok().build();
    }

}