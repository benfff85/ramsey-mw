package com.setminusx.ramsey.mw.model;

import com.setminusx.ramsey.mw.entity.Edge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lightweight work queue item for Redis storage.
 * More compact than full WorkUnit entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkQueueItem {

    private Integer baseGraphId;
    private Integer stageId;
    private List<Edge> edgesToFlip;
    private WorkUnitAnalysisType analysisType;

}
