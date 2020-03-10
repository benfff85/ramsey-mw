package com.setminusx.ramsey.mw.entity;

import com.setminusx.ramsey.mw.model.WorkUnitPriority;
import com.setminusx.ramsey.mw.model.WorkUnitStatus;
import com.setminusx.ramsey.mw.utility.EdgeListConverter;
import lombok.Data;

import javax.persistence.*;
import java.util.Date;
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
    private Date createdDate;
    private Date assignedDate;
    private Date processingStartedDate;
    private Date completedDate;
    private String assignedClient;

    @Enumerated(EnumType.STRING)
    private WorkUnitPriority priority;

}
