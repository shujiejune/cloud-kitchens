package org.example.authservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the X-Operator-Id header injected by the API Gateway after JWT validation
 * and populates the SecurityContext so that @AuthenticationPrincipal resolves
 * to the operatorId Long in downstream controller methods.
 */
@Component
@Slf4j
public class OperatorHeaderAuthFilter extends OncePerRequestFilter {

    static final String OPERATOR_ID_HEADER = "X-Operator-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String operatorIdHeader = request.getHeader(OPERATOR_ID_HEADER);
        if (StringUtils.hasText(operatorIdHeader)) {
            try {
                Long operatorId = Long.parseLong(operatorIdHeader);
                SecurityContextHolder.getContext()
                        .setAuthentication(new OperatorAuthentication(operatorId));
            } catch (NumberFormatException e) {
                log.warn("Invalid {} header value: {}", OPERATOR_ID_HEADER, operatorIdHeader);
            }
        }
        chain.doFilter(request, response);
    }

    public static final class OperatorAuthentication extends AbstractAuthenticationToken {
        private final Long operatorId;

        public OperatorAuthentication(Long operatorId) {
            super(List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
            this.operatorId = operatorId;
            setAuthenticated(true);
        }

        @Override public Object getCredentials() { return null; }
        @Override public Object getPrincipal()   { return operatorId; }
    }
}
