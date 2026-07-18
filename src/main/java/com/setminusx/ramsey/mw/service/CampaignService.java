package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Campaign;
import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.repository.CampaignRepo;
import com.setminusx.ramsey.mw.repository.StageRepo;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepo campaignRepo;
    private final StageRepo stageRepo;

    public List<Campaign> getCampaigns(Integer subgraphSize, Integer vertexCount, Campaign.Status status,
            Campaign.Strategy strategy) {
        List<Campaign> campaigns = (subgraphSize == null && vertexCount == null && strategy == null)
                ? campaignRepo.findAll()
                : campaignRepo.findBySubgraphSizeAndVertexCountAndStrategy(subgraphSize, vertexCount, strategy);

        populateDerivedStatus(campaigns);

        // status is derived (fleet abstraction); apply any status filter after derivation
        // so the /campaigns?status= contract still works.
        if (status != null) {
            return campaigns.stream().filter(c -> status.equals(c.getStatus())).toList();
        }
        return campaigns;
    }

    public Campaign getCampaignById(Integer id) {
        Campaign campaign = campaignRepo.findById(id).orElse(null);
        if (campaign != null) {
            populateDerivedStatus(List.of(campaign));
        }
        return campaign;
    }

    /**
     * Compute the (transient) status of each campaign: ACTIVE iff it has an ACTIVE
     * stage, else INACTIVE. Replaces the removed stored campaign.status column.
     */
    private void populateDerivedStatus(List<Campaign> campaigns) {
        if (campaigns.isEmpty()) {
            return;
        }
        Set<Integer> activeCampaignIds = stageRepo
                .findByCampaignIdAndStatus(null, Stage.Status.ACTIVE)
                .stream()
                .map(Stage::getCampaignId)
                .collect(Collectors.toSet());
        campaigns.forEach(c -> c.setStatus(
                activeCampaignIds.contains(c.getCampaignId())
                        ? Campaign.Status.ACTIVE
                        : Campaign.Status.INACTIVE));
    }

    public Campaign createOrUpdateCampaign(Campaign campaign) {
        return campaignRepo.save(campaign);
    }

    public void deleteCampaign(Integer id) {
        campaignRepo.deleteById(id);
    }

    public List<com.setminusx.ramsey.mw.dto.ProgressionDTO> getCampaignProgression(Integer campaignId) {
        return stageRepo.findProgressionByCampaignId(campaignId);
    }

}
