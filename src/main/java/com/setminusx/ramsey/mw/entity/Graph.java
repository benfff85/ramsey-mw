package com.setminusx.ramsey.mw.entity;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

@Data
@Entity
public class Graph {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer graphId;
    private Integer subgraphSize;
    private Integer vertexCount;
    @Lob
    @Column(length = 41328)
    private String edgeData;
    private Integer cliqueCount;
    private Date identifiedDate;

}
