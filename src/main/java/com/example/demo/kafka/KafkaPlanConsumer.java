package com.example.demo.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.demo.elastic.PlanIndexer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class KafkaPlanConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PlanIndexer indexer;

    public KafkaPlanConsumer(PlanIndexer indexer){ this.indexer = indexer; }

    @KafkaListener(topics = "plan_operations", groupId = "plan-indexer")
    public void listen(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            String op   = root.get("operation").asText();
            JsonNode data = root.get("data");

            switch (op) {
                case "create":
                case "update":
                    indexer.indexOrUpdate(data);
                    break;
                case "delete":
                    indexer.cascadeDelete(data.get("objectId").asText());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
