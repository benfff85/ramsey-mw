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

    // ---------- active-stage caching (the fleet hot path) ----------

    @Test
    void resolveActiveStage_isCachedWithinTheTtl() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(10, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(10, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(10)));

        for (int i = 0; i < 25; i++) {
            assertEquals(999, service.resolveActiveStage("m4-max").orElseThrow().getStageId());
        }
        // Every worker calls this once per cycle; it must not be one DB round trip per call.
        verify(stageRepo, times(1)).findByCampaignIdAndStatus(10, Stage.Status.ACTIVE);
        verify(fleetRepo, times(1)).findById("m4-max");
    }

    /**
     * An empty answer must NOT be cached. A stage advance briefly leaves a campaign with no ACTIVE
     * stage, and a worker seeing that gap treats the fleet as paused and drops its cached graph and
     * hoist tables. Caching the gap would stretch a millisecond race into the full TTL — which,
     * during a descent, is longer than a whole stage.
     */
    @Test
    void resolveActiveStage_doesNotCacheTheEmptyResult() {
        when(fleetRepo.findById("m1")).thenReturn(Optional.of(fleet(10, Fleet.Status.PAUSED)));

        for (int i = 0; i < 10; i++) {
            assertTrue(service.resolveActiveStage("m1").isEmpty());
        }
        verify(fleetRepo, times(10)).findById("m1");
    }

    /** A stage appearing after a gap must be visible immediately, not after the TTL. */
    @Test
    void stageAppearingAfterAGapIsVisibleImmediately() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(10, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(10, Stage.Status.ACTIVE)).thenReturn(List.of());
        assertTrue(service.resolveActiveStage("m4-max").isEmpty()); // mid-advance gap

        when(stageRepo.findByCampaignIdAndStatus(10, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(10)));
        assertTrue(service.resolveActiveStage("m4-max").isPresent(),
                "a new stage must not be hidden by a cached gap");
    }

    /** Pausing must take effect on the next poll, not after the TTL. */
    @Test
    void pause_evictsImmediately() {
        when(fleetRepo.findById("m4-max"))
                .thenReturn(Optional.of(fleet(10, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(10, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(10)));
        assertTrue(service.resolveActiveStage("m4-max").isPresent());

        Fleet paused = fleet(10, Fleet.Status.PAUSED);
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(paused));
        when(fleetRepo.save(any(Fleet.class))).thenReturn(paused);
        service.setStatus("m4-max", Fleet.Status.PAUSED);

        assertTrue(service.resolveActiveStage("m4-max").isEmpty(),
                "pause must be visible on the very next resolve");
    }

    /** Repointing a fleet to another campaign must take effect immediately too. */
    @Test
    void update_evictsImmediately() {
        when(fleetRepo.findById("m4-max"))
                .thenReturn(Optional.of(fleet(10, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(10, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(10)));
        assertEquals(10, service.resolveActiveStage("m4-max").orElseThrow().getCampaignId());

        Fleet repointed = fleet(11, Fleet.Status.RUNNING);
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(repointed));
        when(fleetRepo.save(any(Fleet.class))).thenReturn(repointed);
        when(stageRepo.findByCampaignIdAndStatus(11, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(11)));
        FleetUpdateRequest req = new FleetUpdateRequest();
        req.setCampaignId(11);
        service.update("m4-max", req);

        assertEquals(11, service.resolveActiveStage("m4-max").orElseThrow().getCampaignId());
    }

    /** The cache must not leak across fleets. */
    @Test
    void cacheIsPerPlatform() {
        when(fleetRepo.findById("m4-max")).thenReturn(Optional.of(fleet(10, Fleet.Status.RUNNING)));
        when(stageRepo.findByCampaignIdAndStatus(10, Stage.Status.ACTIVE))
                .thenReturn(List.of(activeStage(10)));
        when(fleetRepo.findById("m1")).thenReturn(Optional.of(fleet(10, Fleet.Status.PAUSED)));

        assertTrue(service.resolveActiveStage("m4-max").isPresent());
        assertTrue(service.resolveActiveStage("m1").isEmpty());
    }
}
