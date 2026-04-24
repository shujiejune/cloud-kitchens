package org.example.procurementservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.price-fetch-requests.name:price-fetch-requests}")
    private String priceFetchRequestsTopic;

    @Value("${kafka.topics.price-fetch-requests.partitions:6}")
    private int priceFetchPartitions;

    @Value("${kafka.topics.price-fetch-requests.replicas:1}")
    private int priceFetchReplicas;

    @Value("${kafka.topics.sub-order-dispatch.name:sub-order-dispatch}")
    private String subOrderDispatchTopic;

    @Value("${kafka.topics.sub-order-dispatch.partitions:12}")
    private int subOrderPartitions;

    @Value("${kafka.topics.sub-order-dispatch.replicas:1}")
    private int subOrderReplicas;

    @Bean
    public NewTopic priceFetchRequestsTopic() {
        return TopicBuilder.name(priceFetchRequestsTopic)
                .partitions(priceFetchPartitions)
                .replicas(priceFetchReplicas)
                .build();
    }

    @Bean
    public NewTopic subOrderDispatchTopic() {
        return TopicBuilder.name(subOrderDispatchTopic)
                .partitions(subOrderPartitions)
                .replicas(subOrderReplicas)
                .build();
    }
}
