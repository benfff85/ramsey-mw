package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.FleetUpdateRequest;
import com.setminusx.ramsey.mw.entity.Fleet;
import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.repository.FleetRepo;
import com.setminusx.ramsey.mw.repository.StageRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@lombok.RequiredArgsConstructor
public class FleetService {

    private final FleetRepo fleetRepo;
    private final StageRepo stageRepo;

    public List<Fleet> list() {
        return fleetRepo.findAll();
    }

    public Optional<Fleet> get(String platform) {
        return fleetRepo.findById(platform);
    }

    /**
     * Resolve the stage a fleet's workers should process:
     *  - unknown platform -> empty (caller returns 404)
     *  - PAUSED           -> empty (caller returns 204; mapping preserved)
     *  - unmapped         -> empty (caller returns 204)
     *  - mapped, no ACTIVE stage -> empty (204)
     *  - mapped + ACTIVE stage -> that stage (200)
     * Distinguish 404 from 204 with {@link #get(String)} for existence.
     */
    public Optional<Stage> resolveActiveStage(String platform) {
        Fleet fleet = fleetRepo.findById(platform).orElse(null);
        if (fleet == null || fleet.getStatus() == Fleet.Status.PAUSED || fleet.getCampaignId() == null) {
            return Optional.empty();
        }
        return stageRepo.findByCampaignIdAndStatus(fleet.getCampaignId(), Stage.Status.ACTIVE)
                .stream().findFirst();
    }

    /** Upsert a fleet with a partial update (only supplied fields change). */
    public Fleet update(String platform, FleetUpdateRequest req) {
        Fleet fleet = fleetRepo.findById(platform).orElseGet(() -> {
            Fleet f = new Fleet();
            f.setPlatform(platform);
            f.setStatus(Fleet.Status.RUNNING);
            return f;
        });
        if (req.isCampaignIdPresent()) {
            fleet.setCampaignId(req.getCampaignId());
        }
        if (req.getStatus() != null) {
            fleet.setStatus(req.getStatus());
        }
        if (req.getNote() != null) {
            fleet.setNote(req.getNote());
        }
        return fleetRepo.save(fleet);
    }

    public Optional<Fleet> setStatus(String platform, Fleet.Status status) {
        return fleetRepo.findById(platform).map(fleet -> {
            fleet.setStatus(status);
            return fleetRepo.save(fleet);
        });
    }
}
