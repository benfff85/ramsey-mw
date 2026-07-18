package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.FleetUpdateRequest;
import com.setminusx.ramsey.mw.entity.Fleet;
import com.setminusx.ramsey.mw.entity.Stage;
import com.setminusx.ramsey.mw.service.FleetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Fleet targeting API. A fleet (platform: m4-max/m1/vast-ai) maps to a campaign
 * and has a RUNNING/PAUSED status; repointing/pausing is a DB update here, no
 * worker redeploy. See docs/investigations/fleet-abstraction-plan.md.
 * Internal API — no auth (mw is on the internal network).
 */
@Slf4j
@RestController
@RequestMapping("/api/ramsey/fleets")
@lombok.RequiredArgsConstructor
public class FleetController {

    private final FleetService fleetService;

    @GetMapping
    public List<Fleet> list() {
        return fleetService.list();
    }

    @GetMapping("/{platform}")
    public Fleet get(@PathVariable String platform) {
        return fleetService.get(platform)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Fleet not found: " + platform));
    }

    /**
     * The worker's hot call. 200 + Stage when RUNNING and the mapped campaign has
     * an ACTIVE stage; 204 when paused / unmapped / no active stage; 404 unknown platform.
     */
    @GetMapping("/{platform}/active-stage")
    public ResponseEntity<Stage> activeStage(@PathVariable String platform) {
        if (fleetService.get(platform).isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Fleet not found: " + platform);
        }
        return fleetService.resolveActiveStage(platform)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{platform}")
    public Fleet update(@PathVariable String platform, @RequestBody FleetUpdateRequest req) {
        log.info("Updating fleet {}: campaignIdPresent={} campaignId={} status={} note={}",
                platform, req.isCampaignIdPresent(), req.getCampaignId(), req.getStatus(), req.getNote());
        return fleetService.update(platform, req);
    }

    @PostMapping("/{platform}/pause")
    public Fleet pause(@PathVariable String platform) {
        log.info("Pausing fleet {}", platform);
        return fleetService.setStatus(platform, Fleet.Status.PAUSED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Fleet not found: " + platform));
    }

    @PostMapping("/{platform}/resume")
    public Fleet resume(@PathVariable String platform) {
        log.info("Resuming fleet {}", platform);
        return fleetService.setStatus(platform, Fleet.Status.RUNNING)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Fleet not found: " + platform));
    }
}
