package com.setminusx.ramsey.mw.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Edge {
    private Integer vertexOne;
    private Integer vertexTwo;

    @Override
    public String toString() {
        return "{" + vertexOne + ":" + vertexTwo + "}";
    }
}
