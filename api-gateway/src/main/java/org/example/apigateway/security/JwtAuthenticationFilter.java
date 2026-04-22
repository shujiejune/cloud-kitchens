package org.example.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.apigateway.exception.JwtValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates the JWT on every non-public request and forwards operator identity
 * to downstream services as {@code X-Operator-Id} / {@code X-Operator-Email} headers.
 *
 * Parsing mirrors auth-service's JwtService (deliberate duplication — services
 * deploy independently, no shared lib).
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BLOCKLIST_KEY_PREFIX = "jwt:blocklist:";
    private static final String BEARER = "Bearer ";

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/actuator"
    );

    private final SecretKey secretKey;
    private final ReactiveStringRedisTemplate redis;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret,
            ReactiveStringRedisTemplate redis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redis     = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER)) {
            return Mono.error(new JwtValidationException("Missing or malformed Authorization header"));
        }

        String token = authHeader.substring(BEARER.length()).trim();
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return Mono.error(new JwtValidationException("Invalid or expired token", e));
        }

        String jti = claims.getId();
        return redis.hasKey(BLOCKLIST_KEY_PREFIX + jti)
                .flatMap(blocked -> {
                    if (Boolean.TRUE.equals(blocked)) {
                        return Mono.error(new JwtValidationException("Token has been revoked"));
                    }
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .header("X-Operator-Id", claims.getSubject())
                            .header("X-Operator-Email", claims.get("email", String.class))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    private boolean isPublic(String path) {
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
