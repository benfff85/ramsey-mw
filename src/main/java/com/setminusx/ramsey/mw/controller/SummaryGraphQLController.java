package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.model.SummaryResponse;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.service.WorkUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Slf4j
@Controller
public class SummaryGraphQLController {

    private final WorkUnitService workUnitService;

    public SummaryGraphQLController(WorkUnitService workUnitService) {
        this.workUnitService = workUnitService;
    }

    @QueryMapping
    public SummaryResponse summary() {
        return new SummaryResponse();
    }

    @SchemaMapping(typeName = "SummaryResponse")
    public SummaryResponse.StageSummary stageSummary(@Argument Integer stageId, @Argument List<WorkUnitStatus> workUnitStatusList) {
        log.info("Fetching stage summary for stageId: {}, workUnitStatusList: {}", stageId, workUnitStatusList);
        SummaryResponse.StageSummary stageSummary = new SummaryResponse.StageSummary();
        stageSummary.setStageId(stageId);
        stageSummary.setWorkUnitStatusList(workUnitStatusList);
        stageSummary.setWorkUnitCount(workUnitService.getWorkUnitCountByStageIdAndStatus(stageId, workUnitStatusList));
        return stageSummary;
    }
}
