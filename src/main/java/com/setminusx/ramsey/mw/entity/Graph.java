package com.setminusx.ramsey.mw.entity;

import jakarta.persistence.*;
import lombok.Data;

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
    /** Graph this one was derived from by applying {@link #flippedEdges}. Null on pre-lineage rows and kick seeds. */
    private Integer parentGraphId;
    /** Edges flipped from the parent, "{{v1:v2},{v3:v4}}". Null when the row has no parent. */
    @Column(length = 512)
    private String flippedEdges;
    /** SHA-256 hex of the '0'/'1' edge string: the processed_graph_hashes key. Null on pre-lineage rows. */
    @Column(length = 64)
    private String graphHash;
    /** Delta hops to the nearest snapshot: 0 = this row stores its edge data; null = written before lineage. */
    private Integer lineageDepth;
    private Integer cliqueCount;
    private Date identifiedDate;

}
