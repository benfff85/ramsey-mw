package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.model.WorkQueueItem;
import com.setminusx.ramsey.mw.service.RedisQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Redis work queue operations.
 * Provides fast queue access for workers.
 */
@Slf4j
@RestController
@RequestMapping("/api/ramsey/queue")
@lombok.RequiredArgsConstructor
public class WorkQueueController {

    private final RedisQueueService redisQueueService;

    /**
     * Pop work items from the queue.
     * Workers call this to get work to process.
     */
    @PostMapping("/pop")
    public List<WorkQueueItem> popWorkItems(
            @RequestParam Integer stageId,
            @RequestParam(defaultValue = "100") Integer count) {
        log.debug("Popping {} items from queue for stage {}", count, stageId);
        List<WorkQueueItem> items = redisQueueService.popWorkItems(stageId, count);
        log.debug("Popped {} items from queue", items.size());
        return items;
    }

    /**
     * Get the current queue depth (O(1) operation).
     */
    @GetMapping("/depth")
    public Long getQueueDepth(@RequestParam Integer stageId) {
        return redisQueueService.getQueueDepth(stageId);
    }

    /**
     * Push work items to the queue.
     * Called by queue-manager to add work.
     */
    @PostMapping("/push")
    public void pushWorkItems(
            @RequestParam Integer stageId,
            @RequestBody List<WorkQueueItem> items) {
        log.info("Pushing {} items to queue for stage {}", items.size(), stageId);
        redisQueueService.pushWorkItems(stageId, items);
    }

    /**
     * Clear the queue for a stage.
     */
    @DeleteMapping
    public void clearQueue(@RequestParam Integer stageId) {
        log.info("Clearing queue for stage {}", stageId);
        redisQueueService.clearQueue(stageId);
    }

}
