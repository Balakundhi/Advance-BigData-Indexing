package com.example.demo.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange("plan.exchange");
    }

    // Queue
    @Bean
    public Queue queue() {
        return new Queue("plan.queue", true); // durable queue
    }

    // Binding
    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("plan.routingkey");
    }
}
