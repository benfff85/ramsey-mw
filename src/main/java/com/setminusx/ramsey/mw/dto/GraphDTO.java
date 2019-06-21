package com.setminusx.ramsey.mw.dto;

import java.util.Date;

public class GraphDTO {

    private Integer graphId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private String edgeData;
    private Integer cliqueCount;
    private Date identifiedDate;

    public GraphDTO() {}

    public GraphDTO(Integer subgraphSize, Integer vertexCount, String edgeData, Integer cliqueCount, Date identifiedDate) {
        this.subgraphSize = subgraphSize;
        this.vertexCount = vertexCount;
        this.edgeData = edgeData;
        this.cliqueCount = cliqueCount;
        this.identifiedDate = identifiedDate;
    }

    public Integer getGraphId() {
        return graphId;
    }

    public void setGraphId(Integer graphId) {
        this.graphId = graphId;
    }

    public Integer getSubgraphSize() {
        return subgraphSize;
    }

    public void setSubgraphSize(Integer subgraphSize) {
        this.subgraphSize = subgraphSize;
    }

    public Integer getVertexCount() {
        return vertexCount;
    }

    public void setVertexCount(Integer vertexCount) {
        this.vertexCount = vertexCount;
    }

    public String getEdgeData() {
        return edgeData;
    }

    public void setEdgeData(String edgeData) {
        this.edgeData = edgeData;
    }

    public Integer getCliqueCount() {
        return cliqueCount;
    }

    public void setCliqueCount(Integer cliqueCount) {
        this.cliqueCount = cliqueCount;
    }

    public Date getIdentifiedDate() {
        return identifiedDate;
    }

    public void setIdentifiedDate(Date identifiedDate) {
        this.identifiedDate = identifiedDate;
    }

}
