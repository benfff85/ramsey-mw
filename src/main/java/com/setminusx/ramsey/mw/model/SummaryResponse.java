package com.setminusx.ramsey.mw.model;

import lombok.Data;
import java.util.List;

@Data
public class SummaryResponse {
    @Data
    public static class StageSummary {
        private long workUnitCount;
        private int stageId;
        private List<WorkUnitStatus> workUnitStatusList;
    }

    @Data
    public static class ClientSummary {
        private long workUnitCount;
        private String clientId;
        private List<WorkUnitStatus> workUnitStatusList;
    }
}
