package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.entity.Campaign;
import com.setminusx.ramsey.mw.service.CampaignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/ramsey/campaigns")
@lombok.RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public List<Campaign> getCampaigns(
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) Campaign.Status status,
            @RequestParam(required = false) Campaign.Strategy strategy) {

        log.info("Fetching Campaigns with filters - SubgraphSize: {}, VertexCount: {}, Status: {}, Strategy: {}",
                subgraphSize, vertexCount, status, strategy);
        return campaignService.getCampaigns(subgraphSize, vertexCount, status, strategy);
    }

    @GetMapping("/{id}")
    public Campaign getCampaignById(@PathVariable Integer id) {
        log.info("Fetching campaign with ID: {}", id);

        Campaign campaign = campaignService.getCampaignById(id);

        if (campaign == null) {
            log.warn("Campaign with ID: {} not found", id);
            throw new ResponseStatusException(NOT_FOUND, "Campaign not found");
        }

        return campaign;
    }

    @GetMapping("/{id}/progression")
    public List<com.setminusx.ramsey.mw.dto.ProgressionDTO> getCampaignProgression(@PathVariable Integer id) {
        log.info("Fetching progression for campaign with ID: {}", id);
        return campaignService.getCampaignProgression(id);
    }

    @PostMapping
    public Campaign createCampaign(@RequestBody Campaign campaign) {
        log.info("Creating a new campaign with data: {}", campaign);
        return campaignService.createOrUpdateCampaign(campaign);
    }

    @PutMapping("/{id}")
    public Campaign updateCampaign(@PathVariable Integer id, @RequestBody Campaign campaign) {
        log.info("Updating campaign with ID: {} with data: {}", id, campaign);
        campaign.setCampaignId(id);
        return campaignService.createOrUpdateCampaign(campaign);
    }

    @DeleteMapping("/{id}")
    public void deleteCampaign(@PathVariable Integer id) {
        log.info("Deleting campaign with ID: {}", id);
        campaignService.deleteCampaign(id);
    }

}