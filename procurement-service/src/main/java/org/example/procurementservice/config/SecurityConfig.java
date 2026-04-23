package org.example.procurementservice.config;

import lombok.RequiredArgsConstructor;
import org.example.procurementservice.filter.OperatorHeaderAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security for procurement-service.
 *
 * JWT validation happens at the API gateway. The gateway injects
 * {@code X-Operator-Id} into the downstream request, which
 * {@link OperatorHeaderAuthFilter} lifts into the SecurityContext.
 * This service never parses a JWT itself.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final OperatorHeaderAuthFilter operatorHeaderAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(operatorHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
