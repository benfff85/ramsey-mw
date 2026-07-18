package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.FleetUpdateRequest;
import com.setminusx.ramsey.mw.entity.Fleet;
import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.repository.FleetRepo;
import com.setminusx.ramsey.mw.repository.StageRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    @Mock FleetRepo fleetRepo;
    @Mock StageRepo stageRepo;
    @InjectMocks FleetService service;

    private Fleet fleet(Integer campaignId, Fleet.Status status) {
        Fleet f = new Fleet();
        f.setPlatform("m4-max");
        f.setCampaignId(campaignId);
        f.setStatus(status);
        return f;
    }

    private Stage activeStage(int campaignId) {
        Stage s = new Stage();
        s.setStageId(999);
        s.setCampaignId(campaignId);
        s.setStatus(Stage.Status.ACTIVE);
        return s;
    }

    @Test
    void unknownPlatform_resolvesEmpty() {
        when(fleetRepo.findById("nope")).thenReturn(Optional.empty());
        assertTrue(service.resolveActiveStage("nope").isEmpty());
        verifyNoInteractions(stageRepo);
    }

    @Test
    void paused_resolvesEmpty_evenWhenMapped() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(3, Fleet.Status.PAUSED)));
        assertTrue(service.resolveActiveStage("m4-max").isEmpty());
        verifyNoInteractions(stageRepo); // must not query stages when paused
    }

    @Test
    void unmapped_resolvesEmpty() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(null, Fleet.Status.RUNNING)));
        assertTrue(service.resolveActiveStage("m4-max").isEmpty());
        verifyNoInteractions(stageRepo);
    }

    @Test
    void runningMappedWithActiveStage_resolvesThatStage() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(3, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(3, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(3)));
        Optional<Stage> resolved = service.resolveActiveStage("m4-max");
        assertTrue(resolved.isPresent());
        assertEquals(999, resolved.get().getStageId());
    }

    @Test
    void runningMappedButNoActiveStage_resolvesEmpty() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(3, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(3, Stage.Status.ACTIVE)).thenReturn(List.of());
        assertTrue(service.resolveActiveStage("m4-max").isEmpty());
    }

    @Test
    void update_partial_onlyChangesSuppliedFields() {
        Fleet existing = fleet(3, Fleet.Status.RUNNING);
        existing.setNote("orig");
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(existing));
        when(fleetRepo.save(any(Fleet.class))).thenAnswer(i -> i.getArgument(0));

        FleetUpdateRequest req = new FleetUpdateRequest();
        req.setStatus(Fleet.Status.PAUSED); // only status supplied; campaignId + note untouched

        Fleet updated = service.update("m4-max", req);
        assertEquals(Fleet.Status.PAUSED, updated.getStatus());
        assertEquals(3, updated.getCampaignId());   // preserved
        assertEquals("orig", updated.getNote());     // preserved
    }

    @Test
    void update_explicitNullCampaignId_unmaps() {
        Fleet existing = fleet(3, Fleet.Status.RUNNING);
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(existing));
        when(fleetRepo.save(any(Fleet.class))).thenAnswer(i -> i.getArgument(0));

        FleetUpdateRequest req = new FleetUpdateRequest();
        req.setCampaignId(null); // setter marks campaignIdPresent=true → intentional unmap

        Fleet updated = service.update("m4-max", req);
        assertNull(updated.getCampaignId());
    }

    @Test
    void update_createsFleetWhenAbsent() {
        when(fleetRepo.findById("vast-ai")).thenReturn(Optional.empty());
        when(fleetRepo.save(any(Fleet.class))).thenAnswer(i -> i.getArgument(0));
        FleetUpdateRequest req = new FleetUpdateRequest();
        req.setCampaignId(10);
        Fleet created = service.update("vast-ai", req);
        assertEquals("vast-ai", created.getPlatform());
        assertEquals(10, created.getCampaignId());
    }
}
