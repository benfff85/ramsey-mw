package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.model.WorkQueueItem;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing the Redis work queue.
 * Provides O(1) queue operations instead of expensive MySQL queries.
 */
@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class RedisQueueService {

    private static final String QUEUE_KEY_PREFIX = "work_queue:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Push a work item to the queue for a given stage.
     */
    public void pushWorkItem(Integer stageId, WorkQueueItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            redisTemplate.opsForList().leftPush(getQueueKey(stageId), json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize work queue item", e);
        }
    }

    /**
     * Push multiple work items to the queue.
     */
    public void pushWorkItems(Integer stageId, List<WorkQueueItem> items) {
        String queueKey = getQueueKey(stageId);
        for (WorkQueueItem item : items) {
            try {
                String json = objectMapper.writeValueAsString(item);
                redisTemplate.opsForList().leftPush(queueKey, json);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize work queue item", e);
            }
        }
    }

    /**
     * Pop work items from the queue. Returns up to 'count' items.
     * Uses RPOP for FIFO ordering.
     */
    public List<WorkQueueItem> popWorkItems(Integer stageId, int count) {
        String queueKey = getQueueKey(stageId);
        List<WorkQueueItem> items = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String json = redisTemplate.opsForList().rightPop(queueKey);
            if (json == null) {
                break; // Queue is empty
            }
            try {
                WorkQueueItem item = objectMapper.readValue(json, WorkQueueItem.class);
                items.add(item);
            } catch (Exception e) {
                log.error("Failed to deserialize work queue item: {}", json, e);
            }
        }

        return items;
    }

    /**
     * Get the queue depth (O(1) operation!).
     */
    public Long getQueueDepth(Integer stageId) {
        Long size = redisTemplate.opsForList().size(getQueueKey(stageId));
        return size != null ? size : 0L;
    }

    /**
     * Clear the queue for a stage.
     */
    public void clearQueue(Integer stageId) {
        redisTemplate.delete(getQueueKey(stageId));
    }

    private String getQueueKey(Integer stageId) {
        return QUEUE_KEY_PREFIX + stageId;
    }

}
