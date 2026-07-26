package com.setminusx.ramsey.mw.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer stageId;

    @Enumerated(EnumType.STRING)
    private Stage.Status status;

    private Integer baseGraphId;
    private Integer campaignId;

    @Enumerated(EnumType.STRING)
    private WorkEnumerationStrategy workEnumerationStrategy;

    @Column(columnDefinition = "TEXT")
    private String details;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public enum WorkEnumerationStrategy {
        BASIC,
        SINGLE_EDGE_CARDINALITY,
        DUAL_EDGE_CARDINALITY,
        DUAL_EDGE_CARDINALITY_WITH_SINGLES,
        /**
         * Singles then pairs in plain edge order — same work space as
         * DUAL_EDGE_CARDINALITY_WITH_SINGLES, no cardinality scoring or sort.
         *
         * Every service that persists or validates this name needs to know it: the queue manager
         * matches on the string to size the work space, the worker deserializes it into its own
         * enum, and THIS enum decides whether a stage POST is accepted at all. Adding a value to
         * only some of them rejects every stage creation with a 400 and stalls the search.
         */
        SEQUENTIAL_WITH_SINGLES
    }

}
