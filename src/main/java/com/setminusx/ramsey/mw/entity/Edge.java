package com.setminusx.ramsey.mw.entity;

import lombok.Data;

import javax.persistence.*;

@Data
@Embeddable
public class Edge {
    private Integer vertexOne;
    private Integer vertexTwo;
}
