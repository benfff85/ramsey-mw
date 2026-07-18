package com.setminusx.ramsey.mw.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A fleet is a group of workers identified by a stable platform (e.g. m4-max, m1,
 * vast-ai). The fleet's mapping to a campaign — and its running/paused state — is
 * the single source of truth for what those workers process. Repointing or pausing
 * a fleet is a DB update (via {@code /api/ramsey/fleets/...}); no worker redeploy.
 * See docs/investigations/fleet-abstraction-plan.md.
 */
@Data
@Entity
public class Fleet {

    /** Platform identifier: 'm4-max' | 'm1' | 'vast-ai' (extensible). */
    @Id
    private String platform;

    /** Target campaign; null = unmapped (workers idle, no target). */
    private Integer campaignId;

    /** RUNNING = work the mapped campaign; PAUSED = idle but keep the mapping. */
    @Enumerated(EnumType.STRING)
    private Fleet.Status status;

    private String note;
    private LocalDateTime updatedDate;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedDate = LocalDateTime.now();
        if (status == null) {
            status = Status.RUNNING;
        }
    }

    public enum Status {
        RUNNING,
        PAUSED
    }

}
