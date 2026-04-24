package org.example.procurementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.event.PriceFetchRequestedEvent;
import org.example.procurementservice.event.SubOrderDispatchEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.price-fetch-requests.name:price-fetch-requests}")
    private String priceFetchTopic;

    @Value("${kafka.topics.sub-order-dispatch.name:sub-order-dispatch}")
    private String subOrderTopic;

    public void publishPriceFetchRequested(PriceFetchRequestedEvent event) {
        kafkaTemplate.send(priceFetchTopic, event.operatorId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PriceFetchRequestedEvent planId={}: {}",
                                event.planId(), ex.getMessage());
                    }
                });
    }

    public void publishSubOrderDispatch(SubOrderDispatchEvent event) {
        kafkaTemplate.send(subOrderTopic, event.vendorName(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish SubOrderDispatchEvent lineItemId={}: {}",
                                event.lineItemId(), ex.getMessage());
                    }
                });
    }
}
