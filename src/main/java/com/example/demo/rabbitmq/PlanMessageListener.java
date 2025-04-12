package com.example.demo.rabbitmq;

import com.example.demo.elastic.PlanIndexer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PlanMessageListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PlanIndexer indexer;

    public PlanMessageListener(PlanIndexer indexer) {
        this.indexer = indexer;
    }

    @RabbitListener(queues = "plan.queue")
    public void receiveMessage(String message) {
        try {
        	 System.out.println("🔔 Received message from RabbitMQ: " + message);
        	 
            JsonNode root = mapper.readTree(message);
            String operation = root.get("operation").asText();
            JsonNode data = root.get("data");

            switch (operation) {
                case "create":
                case "update":
                    indexer.indexOrUpdate(data);
                    break;
                case "delete":
                    indexer.cascadeDelete(data.get("objectId").asText());
                    break;
                default:
                    System.out.println("Unknown operation: " + operation);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
