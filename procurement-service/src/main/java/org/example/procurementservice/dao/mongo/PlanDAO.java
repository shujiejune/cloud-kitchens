package org.example.procurementservice.dao.mongo;

import org.example.procurementservice.document.Plan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for the plans collection.
 *
 * Plans are identified by their Mongo ObjectId (exposed to clients as planId).
 * Every query is scoped by operatorId to enforce tenant isolation — an operator
 * must never be able to read, override, or submit another operator's plan.
 *
 * TTL (6h) is enforced at the document level via @Indexed(expireAfterSeconds=0)
 * on Plan.ttlExpiry, so expired plans are removed by MongoDB without any
 * explicit cleanup here.
 */
@Repository
public interface PlanDAO extends MongoRepository<Plan, String> {

    /**
     * Loads a plan by its id only if it belongs to the given operator.
     * Returns empty when the id is unknown OR belongs to a different tenant.
     */
    @Query("{ '_id': ?0, 'operatorId': ?1 }")
    Optional<Plan> findByIdAndOperatorId(String id, Long operatorId);

    /**
     * Existence probe used before mutation calls (e.g. override, submit)
     * to decide between 404 and full load.
     */
    boolean existsByIdAndOperatorId(String id, Long operatorId);

    /**
     * Deletes the plan after successful submission so it cannot be replayed.
     * Returns the number of documents actually removed (0 if nothing matched,
     * which the caller treats as a lost-race and maps to 404).
     */
    @Query(value = "{ '_id': ?0, 'operatorId': ?1 }", delete = true)
    long deleteByIdAndOperatorId(String id, Long operatorId);
}
