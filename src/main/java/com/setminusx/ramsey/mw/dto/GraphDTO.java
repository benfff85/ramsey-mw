package com.setminusx.ramsey.mw.dto;

import lombok.Data;

import java.util.Date;

@Data
public class GraphDTO {

    private Integer graphId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private String edgeData;
    private Integer cliqueCount;
    private Date identifiedDate;

}
