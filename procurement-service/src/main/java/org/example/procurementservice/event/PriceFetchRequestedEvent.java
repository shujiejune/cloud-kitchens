package org.example.procurementservice.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Published to the {@code price-fetch-requests} Kafka topic when a generatePlan call
 * needs fresh vendor prices. The consumer runs PriceAggregator + OptimizerService and
 * updates the Plan to READY.
 *
 * All item metadata is embedded so the consumer never re-calls catalog-service
 * (avoids JWT expiry issues on retries).
 */
public record PriceFetchRequestedEvent(

        /** MongoDB _id of the PENDING Plan to update once prices are fetched. */
        String planId,

        Long operatorId,

        /** One entry per catalog item — carries all data the consumer needs. */
        List<ItemFetchSpec> items,

        /** When true, bypass the MongoDB snapshot cache and hit every vendor API. */
        boolean refreshPrices
) {

    public record ItemFetchSpec(
            Long catalogItemId,
            String itemName,
            String unit,
            BigDecimal preferredQty
    ) {}
}
