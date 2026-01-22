package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Campaign;
import com.setminusx.ramsey.mw.repository.CampaignRepo;
import com.setminusx.ramsey.mw.repository.StageRepo;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@lombok.RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepo campaignRepo;
    private final StageRepo stageRepo;

    public List<Campaign> getCampaigns(Integer subgraphSize, Integer vertexCount, Campaign.Status status,
            Campaign.Strategy strategy) {
        if (subgraphSize == null && vertexCount == null && status == null && strategy == null) {
            return campaignRepo.findAll();
        } else {
            return campaignRepo.findBySubgraphSizeAndVertexCountAndStatusAndStrategy(subgraphSize, vertexCount, status,
                    strategy);
        }
    }

    public Campaign getCampaignById(Integer id) {
        return campaignRepo.findById(id).orElse(null);
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
