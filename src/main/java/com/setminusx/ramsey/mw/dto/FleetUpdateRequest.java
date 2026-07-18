package com.setminusx.ramsey.mw.dto;

import com.setminusx.ramsey.mw.entity.Fleet;
import lombok.Data;

/**
 * Partial-update body for PUT /api/ramsey/fleets/{platform}. Only non-null fields
 * are applied. campaignId is wrapped so that an explicit null (unmap) is
 * distinguishable from "field omitted": use {@link #campaignIdPresent}.
 */
@Data
public class FleetUpdateRequest {
    private Integer campaignId;
    private boolean campaignIdPresent; // set true by the setter so null-unmap is intentional
    private Fleet.Status status;
    private String note;

    public void setCampaignId(Integer campaignId) {
        this.campaignId = campaignId;
        this.campaignIdPresent = true;
    }
}
