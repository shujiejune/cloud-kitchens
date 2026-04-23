package org.example.procurementservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Reads the {@code X-Operator-Id} header injected by the API gateway's
 * JwtAuthenticationFilter and populates the Spring SecurityContext with
 * an Authentication whose principal is the {@link Long} operatorId.
 *
 * With this in place, controllers resolve the tenant via
 * {@code @AuthenticationPrincipal Long operatorId} without needing a
 * duplicate JWT parser in each downstream service.
 */
@Component
@Slf4j
public class OperatorHeaderAuthFilter extends OncePerRequestFilter {

    public static final String OPERATOR_ID_HEADER = "X-Operator-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String raw = request.getHeader(OPERATOR_ID_HEADER);
        if (raw != null && !raw.isBlank()) {
            try {
                Long operatorId = Long.valueOf(raw.trim());
                SecurityContextHolder.getContext().setAuthentication(new OperatorAuthentication(operatorId));
            } catch (NumberFormatException e) {
                log.warn("Ignoring malformed {} header: {}", OPERATOR_ID_HEADER, raw);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Minimal Authentication impl whose principal is the operatorId Long. */
    private static final class OperatorAuthentication extends AbstractAuthenticationToken {
        private final Long operatorId;

        OperatorAuthentication(Long operatorId) {
            super(Collections.emptyList());
            this.operatorId = operatorId;
            setAuthenticated(true);
        }

        @Override public Object getCredentials() { return ""; }
        @Override public Object getPrincipal()   { return operatorId; }
    }
}
