package org.example.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Provides the KeyResolver bean used by the RequestRateLimiter gateway filter.
 *
 * Keys authenticated requests by operatorId (set in X-Operator-Id by JwtAuthenticationFilter),
 * falling back to the client IP for unauthenticated paths such as /auth/login.
 * This ensures per-operator rate limits rather than per-server-process limits.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver operatorKeyResolver() {
        return exchange -> {
            String operatorId = exchange.getRequest().getHeaders().getFirst("X-Operator-Id");
            if (operatorId != null) {
                return Mono.just("op:" + operatorId);
            }
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + remoteAddr);
        };
    }
}
