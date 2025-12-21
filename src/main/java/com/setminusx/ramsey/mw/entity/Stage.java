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
    private Integer latestWorkUnitId;

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

}
