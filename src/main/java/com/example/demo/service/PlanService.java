package com.example.demo.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.kafka.PlanPublisher;

import java.util.Set;

@Service
public class PlanService {

    private final RedisTemplate<String, String> redisTemplate;
    private final PlanPublisher publisher;   

    public PlanService(RedisTemplate<String, String> redisTemplate, PlanPublisher publisher) {
        this.redisTemplate = redisTemplate;
        this.publisher = publisher;
    }

    /**
     * Checks if a given objectId exists in Redis.
     */
    public boolean exists(String objectId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(objectId));
    }

    /**
     * Saves the plan JSON using 'objectId' as the key in Redis.
     */
    public void save(String objectId, String json, boolean isCreate) {
        redisTemplate.opsForValue().set(objectId, json);
        publisher.send(isCreate ? "create" : "update", json);
    }

    /* convenience for existing code paths that don’t care about create/update */
    public void save(String objectId, String json) {
        save(objectId, json, false);
    }
    
    
    /**
     * Retrieves the plan JSON from Redis by 'objectId'.
     * Returns null if not found.
     */
    public String get(String objectId) {
        return redisTemplate.opsForValue().get(objectId);
    }

    /**
     * Deletes the plan by 'objectId'.
     */
    public void delete(String objectId) {
        redisTemplate.delete(objectId);
        publisher.send("delete", "{\"objectId\":\""+objectId+"\"}");
    }

    /**
     * Returns all Redis keys (for demo/debug only).
     */
    public Set<String> getAllKeys() {
        return redisTemplate.keys("*");
    }
}
