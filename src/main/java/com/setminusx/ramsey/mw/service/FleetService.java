package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.FleetUpdateRequest;
import com.setminusx.ramsey.mw.entity.Fleet;
import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.repository.FleetRepo;
import com.setminusx.ramsey.mw.repository.StageRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@lombok.RequiredArgsConstructor
public class FleetService {

    /**
     * How long a resolved active stage may be served from memory.
     *
     * {@link #resolveActiveStage} is the fleet's hot path — every worker calls it once per work
     * cycle, so it runs tens of times a second and each call is two DB round trips. The answer
     * only changes when a stage advances (seconds apart) or an operator repoints a fleet, and the
     * latter evicts explicitly, so a very short TTL removes almost all of that load while bounding
     * how long a worker can be handed a stage that has just been superseded. Kept well under a
     * worker's batch duration so it is never the dominant source of staleness.
     */
    private static final long ACTIVE_STAGE_CACHE_MILLIS = 250;

    private final FleetRepo fleetRepo;
    private final StageRepo stageRepo;

    private record CachedStage(Optional<Stage> stage, long expiresAtNanos) {
        boolean isFresh() {
            return System.nanoTime() < expiresAtNanos;
        }
    }

    private final Map<String, CachedStage> activeStageCache = new ConcurrentHashMap<>();

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
        CachedStage cached = activeStageCache.get(platform);
        if (cached != null && cached.isFresh()) {
            return cached.stage();
        }
        Optional<Stage> resolved = resolveActiveStageUncached(platform);
        activeStageCache.put(platform, new CachedStage(
                resolved, System.nanoTime() + ACTIVE_STAGE_CACHE_MILLIS * 1_000_000L));
        return resolved;
    }

    /** The real lookup, bypassing the cache. Visible for tests. */
    Optional<Stage> resolveActiveStageUncached(String platform) {
        Fleet fleet = fleetRepo.findById(platform).orElse(null);
        if (fleet == null || fleet.getStatus() == Fleet.Status.PAUSED || fleet.getCampaignId() == null) {
            return Optional.empty();
        }
        return stageRepo.findByCampaignIdAndStatus(fleet.getCampaignId(), Stage.Status.ACTIVE)
                .stream().findFirst();
    }

    /**
     * Drop a fleet's cached resolution. Called whenever the mapping or status changes so that
     * pausing, resuming or repointing a fleet takes effect on the very next worker poll rather
     * than after the TTL — operators expect those to be immediate.
     */
    private void evict(String platform) {
        activeStageCache.remove(platform);
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
        Fleet saved = fleetRepo.save(fleet);
        evict(platform);
        return saved;
    }

    public Optional<Fleet> setStatus(String platform, Fleet.Status status) {
        return fleetRepo.findById(platform).map(fleet -> {
            fleet.setStatus(status);
            Fleet saved = fleetRepo.save(fleet);
            evict(platform);
            return saved;
        });
    }
}
