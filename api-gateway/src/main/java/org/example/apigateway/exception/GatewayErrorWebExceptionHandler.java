package org.example.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.handler.ResponseStatusExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Converts filter-chain errors into JSON {@link ErrorResponse} bodies.
 * Ordered before Spring's default handler (which runs at -1).
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler extends ResponseStatusExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String error;
        String message;

        if (ex instanceof JwtValidationException || ex instanceof JwtException) {
            status  = HttpStatus.UNAUTHORIZED;
            error   = "UNAUTHORIZED";
            message = ex.getMessage();
        } else if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            status  = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
            error   = status.getReasonPhrase().toUpperCase().replace(' ', '_');
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else {
            status  = HttpStatus.INTERNAL_SERVER_ERROR;
            error   = "INTERNAL_SERVER_ERROR";
            message = ex.getMessage() != null ? ex.getMessage() : "Unexpected error";
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ErrorResponse.of(status.value(), error, message));
        } catch (JsonProcessingException e) {
            body = ("{\"status\":" + status.value() + ",\"error\":\"" + error + "\"}").getBytes();
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
