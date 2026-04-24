package org.example.procurementservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.procurementservice.dao.VendorDAO;
import org.example.procurementservice.dao.mongo.PlanDAO;
import org.example.procurementservice.document.Plan;
import org.example.procurementservice.document.Plan.PlanStatus;
import org.example.procurementservice.entity.Vendor;
import org.example.procurementservice.event.PriceFetchRequestedEvent;
import org.example.procurementservice.exception.PlanGenerationFailedException;
import org.example.procurementservice.service.OptimizerService;
import org.example.procurementservice.service.OptimizerService.ItemMeta;
import org.example.procurementservice.service.OptimizerService.OptimizeResult;
import org.example.procurementservice.service.OptimizerService.VendorMeta;
import org.example.procurementservice.service.PriceAggregator;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consumes {@link PriceFetchRequestedEvent} messages, fans out vendor API calls via
 * {@link PriceAggregator}, runs the optimizer, and updates the Plan to READY.
 *
 * Retries up to 3 times with exponential backoff on transient failures.
 * {@link PlanGenerationFailedException} is not retried (plan missing or already settled).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceFetchConsumer {

    private static final long PLAN_TTL_HOURS = 6;

    private final PriceAggregator priceAggregator;
    private final OptimizerService optimizerService;
    private final VendorDAO vendorDAO;
    private final PlanDAO planDAO;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 5000, multiplier = 3),
            exclude = PlanGenerationFailedException.class
    )
    @KafkaListener(topics = "${kafka.topics.price-fetch-requests.name:price-fetch-requests}",
                   groupId = "procurement-service")
    public void consume(PriceFetchRequestedEvent event) {
        Plan plan = planDAO.findByIdAndOperatorId(event.planId(), event.operatorId())
                .orElseThrow(() -> new PlanGenerationFailedException(
                        "Plan not found or expired: " + event.planId()));

        if (plan.getStatus() != PlanStatus.PENDING) {
            log.debug("Skipping PriceFetchRequestedEvent for already-settled plan {}", event.planId());
            return;
        }

        Map<Long, String> itemsByNameKey = new LinkedHashMap<>();
        List<ItemMeta> itemMetas = new ArrayList<>();
        for (PriceFetchRequestedEvent.ItemFetchSpec spec : event.items()) {
            itemsByNameKey.put(spec.catalogItemId(), spec.itemName());
            itemMetas.add(new ItemMeta(spec.catalogItemId(), spec.itemName(),
                    spec.unit(), spec.preferredQty()));
        }

        PriceAggregator.Result aggregated =
                priceAggregator.aggregate(event.operatorId(), itemsByNameKey, event.refreshPrices());

        Map<Long, VendorMeta> vendorMetaById = vendorDAO.findAll().stream()
                .collect(Collectors.toMap(Vendor::getId, v -> new VendorMeta(v.getId(), v.getName())));

        OptimizeResult optimized = optimizerService.assignOptimal(
                itemMetas, vendorMetaById, aggregated.snapshotsByItem());

        List<String> warnings = new ArrayList<>(aggregated.vendorWarnings());
        for (Long missingId : optimized.missingItems()) {
            warnings.add("No vendor found for item #" + missingId);
        }

        Instant now = Instant.now();
        plan.setItems(optimized.items());
        plan.setTotalCost(optimized.totalCost());
        plan.setEstimatedSavings(optimized.estimatedSavings());
        plan.setVendorSubtotals(optimized.vendorSubtotals());
        plan.setVendorWarnings(warnings);
        plan.setGeneratedAt(now);
        plan.setTtlExpiry(now.plus(PLAN_TTL_HOURS, ChronoUnit.HOURS));
        plan.setStatus(PlanStatus.READY);
        planDAO.save(plan);

        log.info("Plan {} set to READY for operatorId={}", event.planId(), event.operatorId());
    }

    @DltHandler
    public void handleDlt(PriceFetchRequestedEvent event) {
        log.error("Price-fetch DLT: planId={}, operatorId={} — marking plan FAILED",
                event.planId(), event.operatorId());
        planDAO.findByIdAndOperatorId(event.planId(), event.operatorId()).ifPresent(plan -> {
            plan.setStatus(PlanStatus.FAILED);
            planDAO.save(plan);
        });
    }
}
