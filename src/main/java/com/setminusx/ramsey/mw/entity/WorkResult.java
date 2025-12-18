package com.setminusx.ramsey.mw.entity;

import com.setminusx.ramsey.mw.model.WorkUnitAnalysisType;
import com.setminusx.ramsey.mw.utility.EdgeListConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Simplified entity for storing work results.
 * Unlike WorkUnit, this only stores completed results - no status tracking.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "work_result", indexes = {
        @Index(name = "idx_work_result_stage_id", columnList = "stageId"),
        @Index(name = "idx_work_result_base_graph_id", columnList = "baseGraphId")
})
public class WorkResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer baseGraphId;
    private Integer stageId;

    @Convert(converter = EdgeListConverter.class)
    private List<Edge> edgesToFlip;

    private Integer cliqueCount;

    @Enumerated(EnumType.STRING)
    private WorkUnitAnalysisType workUnitAnalysisType;

}
