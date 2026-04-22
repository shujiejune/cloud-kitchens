package org.example.authservice.dao;

import org.example.authservice.entity.Operator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the operators table.
 *
 * Naming convention: xxxDAO as requested.
 * Spring Data JPA generates the implementation at runtime.
 */
@Repository
public interface OperatorDAO extends JpaRepository<Operator, Long> {

    /**
     * Used by AuthService.login() to look up the operator by email,
     * then BCrypt-compare the supplied password against passwordHash.
     */
    Optional<Operator> findByEmail(String email);

    /**
     * Used by AuthService.register() to check whether the email is
     * already taken before persisting a new Operator row.
     */
    boolean existsByEmail(String email);
}
