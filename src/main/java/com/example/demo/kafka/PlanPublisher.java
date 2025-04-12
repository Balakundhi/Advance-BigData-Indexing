package com.example.demo.kafka;

import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlanPublisher {

    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanPublisher(KafkaTemplate<String,String> kafka) {
        this.kafka = kafka;
    }

    public void send(String operation, String planJson) {
        try {
            Map<String,Object> wrapper = Map.of(
                "operation", operation,
                "data",      mapper.readTree(planJson)   // keep it JSON, not string‑escaped
            );
            kafka.send("plan_operations", mapper.writeValueAsString(wrapper));
        } catch (Exception e) {
            throw new RuntimeException("Kafka publish failed", e);
        }
    }
}
