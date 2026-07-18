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

    // DERIVED (not stored): ACTIVE iff the campaign has an ACTIVE stage. Populated
    // by CampaignService. The stored campaign.status column was removed with the
    // fleet abstraction — liveness now derives from stage state (targeting from the
    // fleet mapping). Kept as a read-only reporting flag so the API/UI contract is
    // unchanged. See docs/investigations/fleet-abstraction-plan.md.
    @Transient
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
