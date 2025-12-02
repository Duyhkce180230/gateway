package com.laboratory.gateway.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboratory.gateway.dto.ApiResponse;
import com.laboratory.gateway.service.IdentityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final IdentityService identityService;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(IdentityService identityService, ObjectMapper objectMapper) {
        super(Config.class);
        this.identityService = identityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Lấy token từ header Authorization
            List<String> authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
            if (CollectionUtils.isEmpty(authHeader))
                return unauthenticated(exchange.getResponse());

            String token = authHeader.getFirst().replace("Bearer ", "");
            log.info("Token received: {}", token);

            // Gọi Identity Service để kiểm tra token (Introspect)
            return identityService.introspect(token).flatMap(introspectResponse -> {
                if (introspectResponse.getResult().isValid())
                    return chain.filter(exchange); // Token đúng -> Cho đi tiếp
                else
                    return unauthenticated(exchange.getResponse()); // Token sai -> Chặn
            }).onErrorResume(throwable -> {
                log.error("Error verifying token", throwable);
                return unauthenticated(exchange.getResponse());
            });
        };
    }

    Mono<Void> unauthenticated(ServerHttpResponse response){
        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(1401)
                .message("Unauthenticated")
                .build();

        String body = null;
        try {
            body = objectMapper.writeValueAsString(apiResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    public static class Config {
        // Class cấu hình rỗng, cần thiết cho AbstractGatewayFilterFactory
    }
}