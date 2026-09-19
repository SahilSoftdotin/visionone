package com.visiondigitallab.visionone.eventing.internal;

import com.visiondigitallab.visionone.eventing.api.EventType;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfiguration {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic leadTopic() {
        return topic(EventType.Topics.LEAD);
    }

    @Bean
    public NewTopic frontdeskTopic() {
        return topic(EventType.Topics.FRONTDESK);
    }

    @Bean
    public NewTopic appointmentTopic() {
        return topic(EventType.Topics.APPOINTMENT);
    }

    @Bean
    public NewTopic growthTopic() {
        return topic(EventType.Topics.GROWTH);
    }

    @Bean
    public NewTopic contentTopic() {
        return topic(EventType.Topics.CONTENT);
    }

    @Bean
    public NewTopic workTopic() {
        return topic(EventType.Topics.WORK);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
