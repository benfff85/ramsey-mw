package com.setminusx.ramsey.mw.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer campaignId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private Long totalPairs;

    @Enumerated(EnumType.STRING)
    private Campaign.Strategy strategy;

    @Enumerated(EnumType.STRING)
    private Campaign.Status status;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public enum Strategy {
        COMPREHENSIVE_EDGE_PAIR_MUTATION
    }

}
