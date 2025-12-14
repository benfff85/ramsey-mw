package com.setminusx.ramsey.mw.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Edge {
    private Integer vertexOne;
    private Integer vertexTwo;

    @Override
    public String toString() {
        return "{" + vertexOne + ":" + vertexTwo + "}";
    }
}
