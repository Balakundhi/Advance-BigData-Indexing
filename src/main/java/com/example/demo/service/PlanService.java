package com.example.demo.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PlanService {

    private final RedisTemplate<String, String> redisTemplate;

    public PlanService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
    public void save(String objectId, String json) {
        redisTemplate.opsForValue().set(objectId, json);
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
    }

    /**
     * Returns all Redis keys (for demo/debug only).
     */
    public Set<String> getAllKeys() {
        return redisTemplate.keys("*");
    }
}
