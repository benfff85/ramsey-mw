package com.setminusx.ramsey.mw.entity;

import com.setminusx.ramsey.mw.model.WorkUnitAnalysisType;
import com.setminusx.ramsey.mw.model.WorkUnitPriority;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.utility.EdgeListConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
public class WorkUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer subgraphSize;
    private Integer vertexCount;
    private Integer baseGraphId;

    @Convert(converter = EdgeListConverter.class)
    private List<Edge> edgesToFlip;

    @Enumerated(EnumType.STRING)
    private WorkUnitStatus status;

    private Integer cliqueCount;
    private LocalDateTime createdDate;
    private LocalDateTime assignedDate;
    private LocalDateTime processingStartedDate;
    private LocalDateTime completedDate;
    private String assignedClient;

    @Enumerated(EnumType.STRING)
    private WorkUnitPriority priority;

    @Enumerated(EnumType.STRING)
    private WorkUnitAnalysisType workUnitAnalysisType;

}
