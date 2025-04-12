package com.example.demo.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlanMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(String operation, String planJson) {
        try {
            Map<String, Object> wrapper = Map.of(
                "operation", operation,
                "data", mapper.readTree(planJson)
            );

            rabbitTemplate.convertAndSend("plan.exchange", "plan.routingkey", mapper.writeValueAsString(wrapper));
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ publish failed", e);
        }
    }
}
