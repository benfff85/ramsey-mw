package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Campaign;
import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.repository.CampaignRepo;
import com.setminusx.ramsey.mw.repository.StageRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock CampaignRepo campaignRepo;
    @Mock StageRepo stageRepo;
    @InjectMocks CampaignService service;

    private Campaign campaign(int id) {
        Campaign c = new Campaign();
        c.setCampaignId(id);
        return c;
    }

    private Stage activeStageOf(int campaignId) {
        Stage s = new Stage();
        s.setCampaignId(campaignId);
        s.setStatus(Stage.Status.ACTIVE);
        return s;
    }

    @Test
    void statusDerived_activeWhenCampaignHasActiveStage_elseInactive() {
        when(campaignRepo.findAll()).thenReturn(List.of(campaign(10), campaign(11), campaign(12)));
        // only campaigns 10 and 12 have an active stage
        when(stageRepo.findByCampaignIdAndStatus(isNull(), eq(Stage.Status.ACTIVE)))
                .thenReturn(List.of(activeStageOf(10), activeStageOf(12)));

        List<Campaign> result = service.getCampaigns(null, null, null, null);

        assertEquals(Campaign.Status.ACTIVE, byId(result, 10).getStatus());
        assertEquals(Campaign.Status.INACTIVE, byId(result, 11).getStatus());
        assertEquals(Campaign.Status.ACTIVE, byId(result, 12).getStatus());
    }

    @Test
    void statusFilter_appliedAfterDerivation() {
        when(campaignRepo.findAll()).thenReturn(List.of(campaign(10), campaign(11)));
        when(stageRepo.findByCampaignIdAndStatus(isNull(), eq(Stage.Status.ACTIVE)))
                .thenReturn(List.of(activeStageOf(10)));

        List<Campaign> active = service.getCampaigns(null, null, Campaign.Status.ACTIVE, null);
        assertEquals(1, active.size());
        assertEquals(10, active.getFirst().getCampaignId());

        List<Campaign> inactive = service.getCampaigns(null, null, Campaign.Status.INACTIVE, null);
        assertEquals(1, inactive.size());
        assertEquals(11, inactive.getFirst().getCampaignId());
    }

    @Test
    void getById_derivesStatus() {
        when(campaignRepo.findById(10)).thenReturn(Optional.of(campaign(10)));
        when(stageRepo.findByCampaignIdAndStatus(isNull(), eq(Stage.Status.ACTIVE)))
                .thenReturn(List.of(activeStageOf(10)));
        assertEquals(Campaign.Status.ACTIVE, service.getCampaignById(10).getStatus());
    }

    private static Campaign byId(List<Campaign> cs, int id) {
        return cs.stream().filter(c -> c.getCampaignId() == id).findFirst().orElseThrow();
    }
}
