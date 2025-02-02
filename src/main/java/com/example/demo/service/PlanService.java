package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class PlanService {

    private final Map<String, String> planStore = new ConcurrentHashMap<>();

    public boolean exists(String objectId) {
        return planStore.containsKey(objectId);
    }

    public void save(String objectId, String json) {
        planStore.put(objectId, json);
    }

    public String get(String objectId) {
        return planStore.get(objectId);
    }

    public void delete(String objectId) {
        planStore.remove(objectId);
    }
}
